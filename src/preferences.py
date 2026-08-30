"""
Gestion des préférences conducteur
======================================
Charge/sauvegarde les préférences depuis un fichier JSON (une entrée
par conducteur), permet une saisie interactive guidée par un schéma
('_schema' dans le JSON), et "applique" les préférences une fois qu'un
conducteur est identifié.

La structure des préférences (catégories, champs) est définie une
seule fois dans '_schema' du fichier JSON — ce module n'a besoin
d'aucune modification si le schéma change plus tard (nouvelles
catégories, champs supplémentaires, etc.).
"""

import json
import os


def _load_raw(path):
    """Charge le fichier JSON tel quel (avec _comment et _schema inclus)."""
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _save_raw(data, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def load_preferences(path="../preferences.json"):
    """
    Charge le fichier de préférences. Retourne un dict {driver_name: {...}},
    sans les clés internes '_comment' et '_schema'.
    """
    raw = _load_raw(path)
    if not raw:
        print(f"[!] Fichier de préférences introuvable : {path}")
    raw.pop("_comment", None)
    raw.pop("_schema", None)
    return raw


def get_schema(path="../preferences.json"):
    """
    Retourne le schéma de préférences ('_schema' du JSON) — la structure
    utilisée pour guider la saisie interactive. Dict vide si absent.
    """
    raw = _load_raw(path)
    return raw.get("_schema", {})


def get_preferences(driver_name, all_preferences):
    """
    Retourne le dict de préférences d'un conducteur, ou None s'il n'en
    a pas encore (enrôlé pour la voix, mais préférences pas encore
    configurées).
    """
    return all_preferences.get(driver_name)


def set_driver_preferences(driver_name, driver_preferences, path="../preferences.json"):
    """
    Enregistre (ou met à jour) les préférences d'un conducteur dans le
    fichier JSON, en conservant '_comment'/'_schema' déjà présents.
    """
    raw = _load_raw(path)
    raw[driver_name] = driver_preferences
    _save_raw(raw, path)


def prompt_preferences_interactive(schema):
    """
    Parcourt le schéma (dict imbriqué) et demande une valeur pour chaque
    champ terminal, avec la valeur du schéma proposée comme défaut
    (Entrée pour la garder). Retourne un dict de même forme que schema,
    rempli avec les réponses.

    Fonctionne quelle que soit la profondeur/structure du schéma — pas
    besoin de modifier ce code si de nouvelles catégories sont ajoutées.
    """
    if not schema:
        print("[!] Aucun schéma de préférences défini ('_schema' manquant dans le JSON).")
        return {}

    result = {}
    for key, value in schema.items():
        if isinstance(value, dict):
            print(f"\n-- {key} --")
            result[key] = prompt_preferences_interactive(value)
        else:
            typed = input(f"  {key} [{value}] : ").strip()
            if typed == "":
                result[key] = value  # garde la valeur par défaut du schéma
            else:
                # essaie de garder le même type que la valeur par défaut (int/float)
                try:
                    if isinstance(value, int):
                        result[key] = int(typed)
                    elif isinstance(value, float):
                        result[key] = float(typed)
                    else:
                        result[key] = typed
                except ValueError:
                    print(f"  [!] Valeur invalide, garde la valeur par défaut ({value}).")
                    result[key] = value
    return result


def apply_preferences(driver_name, preferences):
    """
    "Applique" les préférences d'un conducteur — pour l'instant, se
    contente d'un affichage structuré. Point d'intégration pour le
    matériel réel plus tard.
    """
    if preferences is None:
        print(f"[!] Aucune préférence enregistrée pour '{driver_name}'.")
        return

    print(f"\n=== Chargement des préférences de '{driver_name}' ===")
    _print_nested(preferences, indent=1)

    # ------------------------------------------------------------------
    # TODO actionneur : c'est ici que chaque catégorie serait envoyée
    # au matériel réel une fois disponible, par exemple :
    #
    #   if "seat" in preferences:
    #       seat_actuator.set_position(preferences["seat"])
    #   if "mirrors" in preferences:
    #       mirror_actuator.set_angles(preferences["mirrors"])
    #   if "climate" in preferences:
    #       climate_control.set(preferences["climate"])
    #
    # Pour l'instant, aucun matériel n'est branché — seul l'affichage
    # ci-dessus simule le comportement attendu.
    # ------------------------------------------------------------------


def _print_nested(d, indent=0):
    """Affiche un dict imbriqué de façon lisible, quelle que soit sa profondeur."""
    for key, value in d.items():
        prefix = "  " * indent
        if isinstance(value, dict):
            print(f"{prefix}{key}:")
            _print_nested(value, indent + 1)
        else:
            print(f"{prefix}{key}: {value}")
