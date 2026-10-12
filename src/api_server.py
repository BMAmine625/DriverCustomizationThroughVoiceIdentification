"""
API du système de reconnaissance vocale conducteur
======================================================
"""

import os
import json
import shutil
import asyncio
import queue
import signal
import tempfile
import threading
import time

from typing import Optional

from fastapi import FastAPI, File, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
import soundfile as sf

from common import load_model
from identification import (
    CONFIDENT_THRESHOLD,
    adapt_profile,
    identify,
    identify_array,
    load_enrolled,
    save_enrolled,
)
from audio_capture import MIN_SPEECH_SECONDS, SAMPLE_RATE, speech_segments
from enrollment import enroll_one_speaker
from preferences import (
    apply_preferences,
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
OPTIONS_PATH = os.environ.get("VOICE_ID_OPTIONS", "../models/driver_options.json")

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
options_lock = threading.Lock()


# ======================================================================
# Options par conducteur (ex. adaptation automatique du profil)
# ======================================================================
# Fichier JSON { "<conducteur>": {"adapt": true/false} }. Par défaut, l'adaptation est DÉSACTIVÉE.

def _load_options() -> dict:
    try:
        with open(OPTIONS_PATH, encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def _save_options(data: dict) -> None:
    os.makedirs(os.path.dirname(OPTIONS_PATH) or ".", exist_ok=True)
    tmp = OPTIONS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, OPTIONS_PATH)


def get_adapt_option(driver_name: str) -> bool:
    with options_lock:
        return bool(_load_options().get(driver_name, {}).get("adapt", False))


def set_adapt_option(driver_name: str, value: bool) -> None:
    with options_lock:
        data = _load_options()
        data.setdefault(driver_name, {})["adapt"] = bool(value)
        _save_options(data)


def delete_options(driver_name: str) -> None:
    with options_lock:
        data = _load_options()
        if data.pop(driver_name, None) is not None:
            _save_options(data)


def rename_options(old_name: str, new_name: str) -> None:
    with options_lock:
        data = _load_options()
        if old_name in data:
            data[new_name] = data.pop(old_name)
            _save_options(data)


def _speaker_dir(driver_name: str) -> str:
    """Dossier d'échantillons d'un conducteur ; refuse les noms qui sortiraient de DATASET_DIR."""
    name = driver_name.strip()
    if not name or name in (".", "..") or "/" in name or "\\" in name or "\x00" in name:
        raise HTTPException(status_code=422, detail="Nom de conducteur invalide.")
    return os.path.join(DATASET_DIR, name)


def _count_samples(driver_name: str) -> int:
    try:
        folder = _speaker_dir(driver_name)
        return len([f for f in os.listdir(folder) if f.lower().endswith((".wav", ".flac"))])
    except (HTTPException, OSError):
        return 0


def _identify_maybe_adapt(embedding_fn, enrolled, adapt_override: Optional[bool]):
    """
    Identifie puis, si le profil identifié a l'adaptation activée (ou si [adapt_override] force le
    comportement pour tous), met à jour son embedding sur une identification très confiante.
    À appeler sous enrolled_lock. Retourne (speaker, score, scores, adapted).
    """
    speaker, score, scores, embedding = embedding_fn()
    adapted = False
    if speaker != "INCONNU" and score >= CONFIDENT_THRESHOLD:
        allowed = adapt_override if adapt_override is not None else get_adapt_option(speaker)
        if allowed:
            adapt_profile(enrolled, speaker, embedding)
            save_enrolled(enrolled, MODELS_PATH)
            adapted = True
    return speaker, score, scores, adapted


def _stdin_quit_listener() -> None:
    """Type 'q' + Enter at any time to stop the server cleanly.

    This sends this process a real SIGINT — exactly what Ctrl+C sends —
    so uvicorn's own signal handler shuts it down gracefully and
    releases the port. It works no matter how the server was launched
    (`python api_server.py` or the `uvicorn api_server:app ...` command
    line), because it doesn't depend on being inside the `__main__`
    block or holding a reference to the Server object — it's registered
    from a FastAPI startup event, which fires either way.
    """
    while True:
        try:
            typed = input()
        except EOFError:
            break
        if typed.strip().lower() == "q":
            os.kill(os.getpid(), signal.SIGINT)
            break


@app.on_event("startup")
def on_startup():
    global classifier, enrolled_speakers
    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=PRETRAINED_DIR)
    if os.path.exists(MODELS_PATH):
        enrolled_speakers = load_enrolled(MODELS_PATH)
    print(f"Conducteurs enrôlés : {list(enrolled_speakers.keys())}")
    print("Type 'q' then Enter at any time to stop the server cleanly.")
    threading.Thread(target=_stdin_quit_listener, daemon=True).start()


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
    delete_options(driver_name)

    speaker_dir = _speaker_dir(driver_name)
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
    rename_options(old_name, new_name)

    old_dir = _speaker_dir(old_name)
    new_dir = _speaker_dir(new_name)
    if os.path.isdir(old_dir) and not os.path.exists(new_dir):
        os.rename(old_dir, new_dir)

    return {"status": "ok", "old_name": old_name, "new_name": new_name}


@app.get("/drivers/{driver_name}/options")
def get_driver_options(driver_name: str):
    """Options du conducteur : adaptation automatique du profil + nombre d'échantillons audio conservés."""
    with enrolled_lock:
        if driver_name not in enrolled_speakers:
            raise HTTPException(status_code=404, detail=f"Conducteur '{driver_name}' introuvable.")
    return {"adapt": get_adapt_option(driver_name), "samples": _count_samples(driver_name)}


@app.put("/drivers/{driver_name}/options")
def set_driver_options(driver_name: str, body: dict):
    with enrolled_lock:
        if driver_name not in enrolled_speakers:
            raise HTTPException(status_code=404, detail=f"Conducteur '{driver_name}' introuvable.")
    if "adapt" not in body or not isinstance(body["adapt"], bool):
        raise HTTPException(status_code=422, detail="Champ 'adapt' (booléen) requis.")
    set_adapt_option(driver_name, body["adapt"])
    return {"status": "ok", "driver": driver_name, "adapt": body["adapt"]}


@app.post("/drivers/{driver_name}/reenroll")
def reenroll_driver(driver_name: str):
    """
    Ré-enrôle un conducteur à partir des échantillons audio DÉJÀ enregistrés (aucun enregistrement) :
    l'embedding est recalculé depuis le dossier du conducteur, ce qui remplace aussi toute adaptation
    accumulée.
    """
    if classifier is None:
        raise HTTPException(status_code=503, detail="Modèle non encore chargé, réessayez dans un instant.")
    speaker_dir = _speaker_dir(driver_name)
    if _count_samples(driver_name) == 0:
        raise HTTPException(status_code=409, detail=f"Aucun échantillon audio enregistré pour '{driver_name}'.")
    with enrolled_lock:
        result = enroll_one_speaker(classifier, speaker_dir)
        if result is None:
            raise HTTPException(status_code=409, detail="Aucun échantillon exploitable.")
        embedding, n_total = result
        enrolled_speakers[driver_name] = embedding
        save_enrolled(enrolled_speakers, MODELS_PATH)
    return {"status": "ok", "driver": driver_name, "samples_total": n_total}


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
async def identify_file(file: UploadFile = File(...), adapt: Optional[bool] = Query(None)):
    if classifier is None:
        raise HTTPException(status_code=503, detail="Modèle non encore chargé, réessayez dans un instant.")

    suffix = os.path.splitext(file.filename or "sample.wav")[1] or ".wav"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    try:
        with enrolled_lock:
            speaker, score, scores, adapted = _identify_maybe_adapt(
                lambda: identify(classifier, tmp_path, enrolled_speakers), enrolled_speakers, adapt
            )
    finally:
        os.remove(tmp_path)

    preferences = None
    if speaker != "INCONNU":
        all_prefs = load_preferences(PREFERENCES_PATH)
        preferences = get_preferences(speaker, all_prefs)
        if preferences is not None:
            apply_preferences(speaker, preferences)

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
async def ws_identify(websocket: WebSocket, adapt: Optional[bool] = Query(None), levels: bool = Query(False)):
    await websocket.accept()

    result_queue = queue.Queue()
    stop_event = threading.Event()

    last_level_sent = [0.0]
    LEVEL_PERIOD = 0.05   # ~20 messages/s max

    def on_level(rms, speaking):
        now = time.monotonic()
        if now - last_level_sent[0] >= LEVEL_PERIOD:
            last_level_sent[0] = now
            result_queue.put({"type": "level", "rms": round(rms, 4), "speaking": speaking})

    def worker():
        all_prefs = load_preferences(PREFERENCES_PATH)
        try:
            for segment in speech_segments(
                stop_event=stop_event,
                on_level=on_level if levels else None,
            ):
                with enrolled_lock:
                    speaker, score, scores, adapted = _identify_maybe_adapt(
                        lambda: identify_array(classifier, segment, enrolled_speakers), enrolled_speakers, adapt
                    )

                preferences = get_preferences(speaker, all_prefs) if speaker != "INCONNU" else None
                if preferences is not None:
                    apply_preferences(speaker, preferences)
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
async def ws_enroll(
    websocket: WebSocket,
    driver_name: str = Query(...),
    samples: int = Query(5),
    replace: bool = Query(False),
):
    """
    Enrôlement automatique au micro de la machine serveur.

    replace=false : les nouveaux échantillons s'AJOUTENT à ceux déjà enregistrés.
    replace=true  : les nouveaux échantillons REMPLACENT les anciens. Les anciens fichiers audio ne sont
                    supprimés qu'une fois TOUS les nouveaux enregistrés et l'enrôlement réussi : si
                    l'enregistrement est interrompu ou échoue, l'ancien profil est conservé intact.
    """
    await websocket.accept()

    try:
        speaker_dir = _speaker_dir(driver_name)
    except HTTPException as e:
        await websocket.send_json({"type": "error", "message": e.detail})
        await websocket.close()
        return

    progress_queue = queue.Queue()
    stop_event = threading.Event()

    def worker():
        staging = None
        try:
            if replace:
                os.makedirs(DATASET_DIR, exist_ok=True)
                staging = tempfile.mkdtemp(prefix=".enroll_", dir=DATASET_DIR)
                target_dir = staging
                start_index = 0
            else:
                os.makedirs(speaker_dir, exist_ok=True)
                target_dir = speaker_dir
                existing = [f for f in os.listdir(speaker_dir) if f.lower().endswith(".wav")]
                start_index = len(existing)
            collected = 0

            for segment in speech_segments(stop_event=stop_event):
                idx = start_index + collected
                path = os.path.join(target_dir, f"sample_{idx}.wav")
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
            if replace and collected < samples:
                progress_queue.put({
                    "type": "error",
                    "message": "Enregistrement interrompu, l'ancien profil est conservé.",
                })
                return

            with enrolled_lock:
                result = enroll_one_speaker(classifier, target_dir)
                if result is None:
                    progress_queue.put({"type": "error", "message": "Échec de l'enrôlement."})
                    return
                embedding, n_total = result
                if replace:
                    # Tout est prêt : on remplace seulement maintenant les anciens fichiers audio.
                    if os.path.isdir(speaker_dir):
                        shutil.rmtree(speaker_dir)
                    os.rename(staging, speaker_dir)
                    staging = None
                enrolled_speakers[driver_name] = embedding
                save_enrolled(enrolled_speakers, MODELS_PATH)

            progress_queue.put({"type": "done", "driver": driver_name, "samples_total": n_total})

        except Exception as e:
            progress_queue.put({"type": "error", "message": str(e)})
        finally:
            if staging and os.path.isdir(staging):
                shutil.rmtree(staging, ignore_errors=True)

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