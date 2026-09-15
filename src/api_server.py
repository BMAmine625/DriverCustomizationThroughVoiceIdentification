"""
API du système de reconnaissance vocale conducteur
======================================================
"""

import os
import shutil
import asyncio
import queue
import tempfile
import threading

from fastapi import FastAPI, File, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import soundfile as sf

from common import load_model
from identification import (
    identify,
    identify_and_adapt,
    identify_and_adapt_array,
    identify_array,
    load_enrolled,
    save_enrolled,
)
from audio_capture import MIN_SPEECH_SECONDS, SAMPLE_RATE, speech_segments
from enrollment import enroll_one_speaker
from preferences import (
    delete_driver_preferences,
    get_preferences,
    get_schema,
    load_preferences,
    rename_driver_preferences,
    set_driver_preferences,
    validate_preferences,
)

MODELS_PATH = os.environ.get("VOICE_ID_MODELS", "../models/enrolled_speakers.pkl")
PRETRAINED_DIR = os.environ.get("VOICE_ID_PRETRAINED", "../pretrained_ecapa")
PREFERENCES_PATH = os.environ.get("VOICE_ID_PREFERENCES", "../preferences.json")
DATASET_DIR = os.environ.get("VOICE_ID_DATASET", "../dataset")

app = FastAPI(
    title="Voice Driver Identification API",
    description="Service d'identification vocale et de gestion des préférences conducteur (PFA).",
    version="0.2.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

classifier = None
enrolled_speakers = {}
enrolled_lock = threading.Lock()


@app.on_event("startup")
def on_startup():
    global classifier, enrolled_speakers
    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=PRETRAINED_DIR)
    if os.path.exists(MODELS_PATH):
        enrolled_speakers = load_enrolled(MODELS_PATH)
    print(f"Conducteurs enrôlés : {list(enrolled_speakers.keys())}")


# ======================================================================
# Endpoints REST
# ======================================================================

@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": classifier is not None}


@app.get("/drivers")
def list_drivers():
    with enrolled_lock:
        return {"drivers": list(enrolled_speakers.keys())}


@app.delete("/drivers/{driver_name}")
def delete_driver(driver_name: str):
    """
    Supprime complètement un conducteur : profil vocal (embedding),
    préférences enregistrées, et ses échantillons audio bruts sur disque.
    """
    with enrolled_lock:
        removed_voice = enrolled_speakers.pop(driver_name, None) is not None
        if removed_voice:
            save_enrolled(enrolled_speakers, MODELS_PATH)

    removed_prefs = delete_driver_preferences(driver_name, PREFERENCES_PATH)

    speaker_dir = os.path.join(DATASET_DIR, driver_name)
    if os.path.isdir(speaker_dir):
        shutil.rmtree(speaker_dir)

    if not removed_voice and not removed_prefs:
        raise HTTPException(status_code=404, detail=f"Conducteur '{driver_name}' introuvable.")

    return {"status": "ok", "driver": driver_name, "deleted": True}


@app.post("/drivers/{old_name}/rename")
def rename_driver(old_name: str, new_name: str = Query(...)):
    """
    Renomme un conducteur : déplace son profil vocal, ses préférences,
    et son dossier d'échantillons audio vers le nouveau nom.
    """
    new_name = new_name.strip()
    if not new_name:
        raise HTTPException(status_code=422, detail="Le nouveau nom ne peut pas être vide.")

    with enrolled_lock:
        if old_name not in enrolled_speakers:
            raise HTTPException(status_code=404, detail=f"Conducteur '{old_name}' introuvable.")
        if new_name in enrolled_speakers:
            raise HTTPException(status_code=409, detail=f"'{new_name}' existe déjà.")
        enrolled_speakers[new_name] = enrolled_speakers.pop(old_name)
        save_enrolled(enrolled_speakers, MODELS_PATH)

    rename_driver_preferences(old_name, new_name, PREFERENCES_PATH)

    old_dir = os.path.join(DATASET_DIR, old_name)
    new_dir = os.path.join(DATASET_DIR, new_name)
    if os.path.isdir(old_dir) and not os.path.exists(new_dir):
        os.rename(old_dir, new_dir)

    return {"status": "ok", "old_name": old_name, "new_name": new_name}


@app.get("/preferences/schema")
def preferences_schema():
    schema = get_schema(PREFERENCES_PATH)
    if not schema:
        raise HTTPException(status_code=404, detail="Aucun schéma de préférences défini ('_schema' manquant).")
    return schema


@app.get("/preferences/{driver_name}")
def get_driver_preferences(driver_name: str):
    all_prefs = load_preferences(PREFERENCES_PATH)
    prefs = get_preferences(driver_name, all_prefs)
    if prefs is None:
        raise HTTPException(status_code=404, detail=f"Aucune préférence enregistrée pour '{driver_name}'.")
    return prefs


@app.put("/preferences/{driver_name}")
def set_preferences(driver_name: str, body: dict):
    schema = get_schema(PREFERENCES_PATH)
    if not schema:
        raise HTTPException(status_code=500, detail="Aucun schéma de préférences défini côté serveur.")
    try:
        cleaned = validate_preferences(schema, body)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

    set_driver_preferences(driver_name, cleaned, PREFERENCES_PATH)
    return {"status": "ok", "driver": driver_name, "preferences": cleaned}


@app.post("/identify")
async def identify_file(file: UploadFile = File(...), adapt: bool = Query(False)):
    if classifier is None:
        raise HTTPException(status_code=503, detail="Modèle non encore chargé, réessayez dans un instant.")

    suffix = os.path.splitext(file.filename or "sample.wav")[1] or ".wav"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    try:
        with enrolled_lock:
            if adapt:
                speaker, score, scores, adapted = identify_and_adapt(
                    classifier, tmp_path, enrolled_speakers, save_path=MODELS_PATH
                )
            else:
                speaker, score, scores, _ = identify(classifier, tmp_path, enrolled_speakers)
                adapted = False
    finally:
        os.remove(tmp_path)

    preferences = None
    if speaker != "INCONNU":
        all_prefs = load_preferences(PREFERENCES_PATH)
        preferences = get_preferences(speaker, all_prefs)

    return {
        "speaker": speaker,
        "score": score,
        "scores": scores,
        "adapted": adapted,
        "preferences": preferences,
    }


# ======================================================================
# WebSocket : identification en flux continu
# ======================================================================

@app.websocket("/ws/identify")
async def ws_identify(websocket: WebSocket, adapt: bool = Query(False)):
    await websocket.accept()

    result_queue = queue.Queue()
    stop_event = threading.Event()

    def worker():
        all_prefs = load_preferences(PREFERENCES_PATH)
        try:
            for segment in speech_segments(stop_event=stop_event):
                with enrolled_lock:
                    if adapt:
                        speaker, score, scores, adapted = identify_and_adapt_array(
                            classifier, segment, enrolled_speakers, save_path=MODELS_PATH
                        )
                    else:
                        speaker, score, scores, _ = identify_array(classifier, segment, enrolled_speakers)
                        adapted = False

                preferences = get_preferences(speaker, all_prefs) if speaker != "INCONNU" else None
                result_queue.put({
                    "speaker": speaker,
                    "score": score,
                    "scores": scores,
                    "adapted": adapted,
                    "preferences": preferences,
                })
        except Exception as e:
            result_queue.put({"error": str(e)})

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()

    try:
        while True:
            try:
                item = result_queue.get_nowait()
                await websocket.send_json(item)
            except queue.Empty:
                await asyncio.sleep(0.1)
    except WebSocketDisconnect:
        pass
    finally:
        stop_event.set()
        thread.join(timeout=2)


# ======================================================================
# WebSocket : enrôlement automatique au micro
# ======================================================================

@app.websocket("/ws/enroll")
async def ws_enroll(websocket: WebSocket, driver_name: str = Query(...), samples: int = Query(5)):
    await websocket.accept()

    progress_queue = queue.Queue()
    stop_event = threading.Event()

    def worker():
        speaker_dir = os.path.join(DATASET_DIR, driver_name)
        os.makedirs(speaker_dir, exist_ok=True)
        existing = [f for f in os.listdir(speaker_dir) if f.lower().endswith(".wav")]
        start_index = len(existing)
        collected = 0

        try:
            for segment in speech_segments(stop_event=stop_event):
                idx = start_index + collected
                path = os.path.join(speaker_dir, f"sample_{idx}.wav")
                sf.write(path, segment, SAMPLE_RATE)
                collected += 1
                progress_queue.put({
                    "type": "sample_recorded",
                    "index": collected,
                    "total": samples,
                    "duration": round(len(segment) / SAMPLE_RATE, 2),
                })
                if collected >= samples:
                    break

            if collected == 0:
                progress_queue.put({"type": "error", "message": "Aucun échantillon enregistré."})
                return

            with enrolled_lock:
                result = enroll_one_speaker(classifier, speaker_dir)
                if result is None:
                    progress_queue.put({"type": "error", "message": "Échec de l'enrôlement."})
                    return
                embedding, n_total = result
                enrolled_speakers[driver_name] = embedding
                save_enrolled(enrolled_speakers, MODELS_PATH)

            progress_queue.put({"type": "done", "driver": driver_name, "samples_total": n_total})

        except Exception as e:
            progress_queue.put({"type": "error", "message": str(e)})

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()

    try:
        while True:
            try:
                item = progress_queue.get_nowait()
                await websocket.send_json(item)
                if item.get("type") in ("done", "error"):
                    break
            except queue.Empty:
                await asyncio.sleep(0.1)
    except WebSocketDisconnect:
        stop_event.set()
    finally:
        stop_event.set()
        thread.join(timeout=2)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api_server:app", host="0.0.0.0", port=8000, reload=False)
