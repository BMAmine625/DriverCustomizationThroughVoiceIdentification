"""
Client de test pour les WebSockets de l'API (à usage de test uniquement)
=============================================================================
Permet de tester /ws/identify et /ws/enroll depuis le terminal, en
attendant un vrai client (Kotlin ou autre). Se connecte à l'API qui
tourne en local (ou sur une autre machine, via son adresse IP).

Usage :
    python3 test_ws_client.py identify
    python3 test_ws_client.py identify --adapt
    python3 test_ws_client.py enroll driver_3 --samples 5
    python3 test_ws_client.py identify --host 192.168.1.42   # API sur une autre machine

Dépendance :
    pip install websockets --break-system-packages
"""

import argparse
import asyncio
import json

import websockets


async def run_identify(host, port, adapt):
    url = f"ws://{host}:{port}/ws/identify?adapt={'true' if adapt else 'false'}"
    print(f"Connexion à {url}")
    print("Parle au micro du serveur ; Ctrl+C ici pour arrêter le test.\n")

    async with websockets.connect(url) as ws:
        async for message in ws:
            data = json.loads(message)
            if "error" in data:
                print(f"[ERREUR] {data['error']}")
                continue
            print(f"Conducteur détecté : {data['speaker']} (score: {data['score']:.3f})")
            for name, score in sorted(data["scores"].items(), key=lambda x: -x[1]):
                print(f"    {name}: {score:.3f}")
            if data.get("adapted"):
                print(f"    [OK] Profil de '{data['speaker']}' mis à jour")
            if data.get("preferences"):
                print(f"    Préférences : {data['preferences']}")
            print()


async def run_enroll(host, port, driver_name, samples):
    url = f"ws://{host}:{port}/ws/enroll?driver_name={driver_name}&samples={samples}"
    print(f"Connexion à {url}")
    print(f"Enrôlement de '{driver_name}' — parle au micro du serveur.\n")

    async with websockets.connect(url) as ws:
        async for message in ws:
            data = json.loads(message)
            if data["type"] == "sample_recorded":
                print(f"[OK] Échantillon {data['index']}/{data['total']} ({data['duration']}s)")
            elif data["type"] == "done":
                print(f"\n[OK] Enrôlement terminé : '{data['driver']}' "
                      f"({data['samples_total']} échantillons au total)")
                break
            elif data["type"] == "error":
                print(f"[ERREUR] {data['message']}")
                break


def main():
    parser = argparse.ArgumentParser(description="Client de test pour les WebSockets de l'API")
    sub = parser.add_subparsers(dest="command", required=True)

    p_identify = sub.add_parser("identify", help="Teste l'identification en flux continu")
    p_identify.add_argument("--adapt", action="store_true", help="Active l'adaptation incrémentale")
    p_identify.add_argument("--host", default="localhost")
    p_identify.add_argument("--port", type=int, default=8000)

    p_enroll = sub.add_parser("enroll", help="Teste l'enrôlement automatique")
    p_enroll.add_argument("driver_name")
    p_enroll.add_argument("--samples", type=int, default=5)
    p_enroll.add_argument("--host", default="localhost")
    p_enroll.add_argument("--port", type=int, default=8000)

    args = parser.parse_args()

    if args.command == "identify":
        asyncio.run(run_identify(args.host, args.port, args.adapt))
    elif args.command == "enroll":
        asyncio.run(run_enroll(args.host, args.port, args.driver_name, args.samples))


if __name__ == "__main__":
    main()
