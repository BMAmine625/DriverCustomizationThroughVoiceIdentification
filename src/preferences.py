"""
Gestion des préférences conducteur
======================================
"""

import json
import os


def _load_raw(path):
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _save_raw(data, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def load_preferences(path="../preferences.json"):
    raw = _load_raw(path)
    if not raw:
        print(f"[!] Fichier de préférences introuvable : {path}")
    raw.pop("_comment", None)
    raw.pop("_schema", None)
    return raw


def get_schema(path="../preferences.json"):
    raw = _load_raw(path)
    return raw.get("_schema", {})


def get_preferences(driver_name, all_preferences):
    return all_preferences.get(driver_name)


def set_driver_preferences(driver_name, driver_preferences, path="../preferences.json"):
    raw = _load_raw(path)
    raw[driver_name] = driver_preferences
    _save_raw(raw, path)


def delete_driver_preferences(driver_name, path="../preferences.json"):
    """Supprime les préférences d'un conducteur du fichier JSON, si présentes.
    Retourne True si une entrée a effectivement été supprimée."""
    raw = _load_raw(path)
    if driver_name in raw:
        del raw[driver_name]
        _save_raw(raw, path)
        return True
    return False


def rename_driver_preferences(old_name, new_name, path="../preferences.json"):
    """Renomme la clé d'un conducteur dans le fichier de préférences,
    si une entrée existe pour l'ancien nom. Ne fait rien silencieusement
    si l'ancien nom n'a pas de préférences enregistrées."""
    raw = _load_raw(path)
    if old_name in raw:
        raw[new_name] = raw.pop(old_name)
        _save_raw(raw, path)


def _is_field_spec(value):
    return isinstance(value, dict) and {"min", "max", "default"}.issubset(value.keys())


def _prompt_single_field(key, spec):
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
            typed = input(f"  {key} [{value}] : ").strip()
            result[key] = typed if typed else value
    return result


def validate_preferences(schema, data, _path=""):
    if not isinstance(data, dict):
        raise ValueError(f"'{_path or 'racine'}' doit être un objet JSON.")
    result = {}
    for key, spec in schema.items():
        field_path = f"{_path}.{key}" if _path else key
        if _is_field_spec(spec):
            if key not in data:
                raise ValueError(f"Champ manquant : '{field_path}'.")
            value = data[key]
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ValueError(f"'{field_path}' doit être numérique (reçu : {value!r}).")
            min_v, max_v = spec["min"], spec["max"]
            if not (min_v <= value <= max_v):
                raise ValueError(f"'{field_path}' hors plage ({min_v} à {max_v}), reçu : {value}.")
            result[key] = value
        elif isinstance(spec, dict):
            if key not in data or not isinstance(data[key], dict):
                raise ValueError(f"Catégorie manquante ou invalide : '{field_path}'.")
            result[key] = validate_preferences(spec, data[key], _path=field_path)
    return result


def apply_preferences(driver_name, preferences):
    if preferences is None:
        print(f"[!] Aucune préférence enregistrée pour '{driver_name}'.")
        return
    print(f"\n=== Chargement des préférences de '{driver_name}' ===")
    _print_nested(preferences, indent=1)


def _print_nested(d, indent=0):
    for key, value in d.items():
        prefix = "  " * indent
        if isinstance(value, dict):
            print(f"{prefix}{key}:")
            _print_nested(value, indent + 1)
        else:
            print(f"{prefix}{key}: {value}")
