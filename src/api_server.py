"""
API du système de reconnaissance vocale conducteur
======================================================
Service backend destiné à un client léger (ex. application Kotlin sur
Android Automotive OS). N'exécute AUCUNE logique nouvelle : il se
contente d'orchestrer common.py / identification.py / audio_capture.py
/ preferences.py / enrollment.py déjà existants et testés.

Important — où tourne ce service :
------------------------------------
Le microphone utilisé (identification en flux continu, enrôlement
automatique) est celui de la machine qui EXÉCUTE ce service, pas celui
du client qui s'y connecte. En usage réel, ce service tournerait sur un
petit ordinateur compagnon embarqué dans le véhicule (ex. Raspberry Pi
branché au micro de l'habitacle), et le client (Kotlin/Android
Automotive, ou autre) s'y connecterait via le réseau local du véhicule.

Lancement :
    uvicorn api_server:app --host 0.0.0.0 --port 8000

Variables d'environnement (optionnelles, sinon valeurs par défaut
relatives à src/, comme les autres scripts) :
    VOICE_ID_MODELS       chemin du fichier enrolled_speakers.pkl
    VOICE_ID_PRETRAINED   dossier de cache du modèle ECAPA-TDNN
    VOICE_ID_PREFERENCES  chemin du fichier preferences.json
    VOICE_ID_DATASET      dossier dataset/ (pour l'enrôlement au micro)

Dépendances supplémentaires par rapport aux scripts existants :
    pip install fastapi uvicorn python-multipart --break-system-packages
"""

import os
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
from preferences import get_preferences, get_schema, load_preferences, set_driver_preferences, validate_preferences

MODELS_PATH = os.environ.get("VOICE_ID_MODELS", "../models/enrolled_speakers.pkl")
PRETRAINED_DIR = os.environ.get("VOICE_ID_PRETRAINED", "../pretrained_ecapa")
PREFERENCES_PATH = os.environ.get("VOICE_ID_PREFERENCES", "../preferences.json")
DATASET_DIR = os.environ.get("VOICE_ID_DATASET", "../dataset")

app = FastAPI(
    title="Voice Driver Identification API",
    description="Service d'identification vocale et de gestion des préférences conducteur (PFA).",
    version="0.1.0",
)

# CORS permissif pour faciliter le développement du client (Kotlin, web,
# etc.) sur le réseau local. À restreindre si le service est exposé
# au-delà d'un réseau de confiance.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- État global, chargé une seule fois au démarrage du service ---
classifier = None
enrolled_speakers = {}
enrolled_lock = threading.Lock()  # protège les accès concurrents (REST + WebSocket)


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
    """Vérifie que le service tourne et que le modèle est chargé."""
    return {"status": "ok", "model_loaded": classifier is not None}


@app.get("/drivers")
def list_drivers():
    """Liste les conducteurs actuellement enrôlés."""
    with enrolled_lock:
        return {"drivers": list(enrolled_speakers.keys())}


@app.get("/preferences/schema")
def preferences_schema():
    """
    Retourne le schéma des préférences (catégories, unités, bornes) —
    utile à un client pour construire dynamiquement un formulaire de
    configuration sans coder les catégories en dur.
    """
    schema = get_schema(PREFERENCES_PATH)
    if not schema:
        raise HTTPException(status_code=404, detail="Aucun schéma de préférences défini ('_schema' manquant).")
    return schema


@app.get("/preferences/{driver_name}")
def get_driver_preferences(driver_name: str):
    """Retourne les préférences enregistrées d'un conducteur."""
    all_prefs = load_preferences(PREFERENCES_PATH)
    prefs = get_preferences(driver_name, all_prefs)
    if prefs is None:
        raise HTTPException(status_code=404, detail=f"Aucune préférence enregistrée pour '{driver_name}'.")
    return prefs


@app.put("/preferences/{driver_name}")
def set_preferences(driver_name: str, body: dict):
    """
    Enregistre les préférences d'un conducteur. Le corps de la requête
    doit respecter la structure renvoyée par GET /preferences/schema ;
    toute valeur hors plage ou champ manquant est rejeté (422) avec un
    message précisant le champ fautif.
    """
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
    """
    Identifie un conducteur à partir d'un fichier audio envoyé par le
    client (multipart/form-data). Retourne le conducteur identifié, les
    scores détaillés, et ses préférences si connu.
    """
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
# WebSocket : identification en flux continu (micro du SERVEUR)
# ======================================================================

@app.websocket("/ws/identify")
async def ws_identify(websocket: WebSocket, adapt: bool = Query(False)):
    """
    Pousse un message JSON au client à chaque identification, en
    continu, tant que la connexion reste ouverte. Le microphone utilisé
    est celui de la machine hébergeant ce service (voir docstring du
    module).

    Message envoyé : {speaker, score, scores, adapted, preferences}
    """
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
# WebSocket : enrôlement automatique au micro (SERVEUR), avec progression
# ======================================================================

@app.websocket("/ws/enroll")
async def ws_enroll(websocket: WebSocket, driver_name: str = Query(...), samples: int = Query(5)):
    """
    Enrôle un nouveau conducteur en enregistrant au micro du serveur,
    avec un message de progression après chaque échantillon capté.

    Messages envoyés :
        {"type": "sample_recorded", "index": i, "total": n, "duration": d}
        {"type": "done", "driver": ..., "samples_total": n}
        {"type": "error", "message": "..."}
    """
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
