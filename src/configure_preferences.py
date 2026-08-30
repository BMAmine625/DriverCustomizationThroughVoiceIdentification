"""
Configuration des préférences d'un conducteur (à tout moment)
==================================================================
À utiliser quand un conducteur a choisi de configurer ses préférences
plus tard plutôt qu'au moment de l'enrôlement — ou pour modifier des
préférences déjà enregistrées.

Usage :
    python3 configure_preferences.py driver_1
    python3 configure_preferences.py driver_3 --preferences ../preferences.json
"""

import argparse

from preferences import get_schema, prompt_preferences_interactive, set_driver_preferences


def main():
    parser = argparse.ArgumentParser(description="Configure les préférences d'un conducteur")
    parser.add_argument("driver_name", help="Nom du conducteur (doit correspondre à celui utilisé à l'enrôlement)")
    parser.add_argument("--preferences", default="../preferences.json")
    args = parser.parse_args()

    schema = get_schema(args.preferences)
    if not schema:
        print(f"[!] Aucun schéma de préférences trouvé dans {args.preferences} "
              f"(clé '_schema' manquante).")
        return

    print(f"=== Configuration des préférences de '{args.driver_name}' ===")
    print("(Entrée pour garder la valeur par défaut affichée entre crochets)\n")

    driver_preferences = prompt_preferences_interactive(schema)
    set_driver_preferences(args.driver_name, driver_preferences, args.preferences)

    print(f"\n[OK] Préférences de '{args.driver_name}' enregistrées dans {args.preferences}")


if __name__ == "__main__":
    main()
