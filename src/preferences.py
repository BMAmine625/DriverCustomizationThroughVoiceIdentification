"""
Gestion des préférences conducteur
======================================
Charge/sauvegarde les préférences depuis un fichier JSON (une entrée
par conducteur), permet une saisie interactive guidée et validée par un
schéma ('_schema' dans le JSON), et "applique" les préférences une fois
qu'un conducteur est identifié.

Format du schéma ('_schema') :
-------------------------------
Chaque champ terminal est un dict {"unit": ..., "min": ..., "max": ...,
"default": ...} — utilisé pour afficher l'unité, valider la saisie
(refuse et redemande si hors bornes), et proposer une valeur par défaut.
Les catégories (ex. "seat", "mirrors") sont de simples dicts imbriqués
autour de ces champs terminaux, à n'importe quelle profondeur.

Ce module n'a besoin d'aucune modification si le schéma change plus
tard (nouvelles catégories, champs, bornes différentes, etc.).
"""

import json
import os

_SPEC_KEYS = {"unit", "min", "max", "default"}


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
    utilisée pour guider et valider la saisie interactive. Dict vide si
    absent.
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


def _is_field_spec(value):
    """Un champ terminal du schéma est un dict portant (au moins) min/max/default."""
    return isinstance(value, dict) and {"min", "max", "default"}.issubset(value.keys())


def _prompt_single_field(key, spec):
    """
    Demande une valeur pour un champ terminal, avec validation des
    bornes (min/max). Redemande tant que la saisie est invalide ou hors
    plage. Entrée vide = garde la valeur par défaut.
    """
    unit = spec.get("unit", "")
    min_v, max_v, default = spec["min"], spec["max"], spec["default"]

    while True:
        typed = input(f"  {key} ({unit}, {min_v} à {max_v}) [{default}] : ").strip()
        if typed == "":
            return default
        try:
            value = float(typed) if isinstance(default, float) else int(typed)
        except ValueError:
            print(f"    [!] Valeur invalide, réessaie.")
            continue

        if not (min_v <= value <= max_v):
            print(f"    [!] Hors plage ({min_v} à {max_v}), réessaie.")
            continue

        return value


def prompt_preferences_interactive(schema):
    """
    Parcourt le schéma (dict imbriqué de catégories, avec des champs
    terminaux au format {"unit","min","max","default"}) et demande une
    valeur validée pour chaque champ. Retourne un dict de même forme
    que schema (mais avec de simples valeurs, pas les specs), prêt à
    être sauvegardé comme préférences d'un conducteur.

    Fonctionne quelle que soit la profondeur/structure du schéma — pas
    besoin de modifier ce code si de nouvelles catégories sont ajoutées.
    """
    if not schema:
        print("[!] Aucun schéma de préférences défini ('_schema' manquant dans le JSON).")
        return {}

    result = {}
    for key, value in schema.items():
        if _is_field_spec(value):
            result[key] = _prompt_single_field(key, value)
        elif isinstance(value, dict):
            print(f"\n-- {key} --")
            result[key] = prompt_preferences_interactive(value)
        else:
            # Cas de secours : schéma mal formé (valeur brute au lieu d'un spec dict)
            typed = input(f"  {key} [{value}] : ").strip()
            result[key] = typed if typed else value

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
