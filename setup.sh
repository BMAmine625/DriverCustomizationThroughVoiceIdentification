#!/usr/bin/env bash
# Installation complète du projet en une seule commande.
#
# Usage :
#   ./setup.sh
#
# Ce script :
#   1. Crée un environnement virtuel Python (venv/)
#   2. L'active
#   3. Installe torch + torchaudio en version CPU (alignées entre elles)
#   4. Installe le reste des dépendances (requirements.txt)
#
# Après exécution, pour travailler sur le projet :
#   source venv/bin/activate

set -e  # arrête le script à la première erreur

echo "=== 1/4 - Création de l'environnement virtuel ==="
if [ -d "venv" ]; then
    echo "Le dossier venv/ existe déjà, réutilisation."
else
    python3 -m venv venv
fi

echo ""
echo "=== 2/4 - Activation de l'environnement virtuel ==="
source venv/bin/activate
echo "Python utilisé : $(which python3)"
echo "Version : $(python3 --version)"

echo ""
echo "=== 3/4 - Installation de torch + torchaudio (CPU-only) ==="
# Installés ensemble et depuis le même index pour éviter tout mélange
# CPU/GPU, qui cause des erreurs type "libcudart.so cannot open shared
# object file".
pip install --upgrade pip --quiet
pip install torch torchaudio \
    --index-url https://download.pytorch.org/whl/cpu \
    --no-cache-dir \
    --timeout 300

echo ""
echo "=== 4/4 - Installation des autres dépendances ==="
pip install -r requirements.txt --no-cache-dir --timeout 300

echo ""
echo "=== Vérification ==="
python3 -c "import torch; print('torch      :', torch.__version__, '(CUDA dispo:', torch.cuda.is_available(), ')')"
python3 -c "import torchaudio; print('torchaudio :', torchaudio.__version__)"
python3 -c "import speechbrain; print('speechbrain: OK')"
python3 -c "import librosa; print('librosa    : OK')"

echo ""
echo "=== Installation terminée avec succès ==="
echo ""
echo "Pour travailler sur le projet, active l'environnement virtuel :"
echo "    source venv/bin/activate"
echo ""
echo "Puis, depuis src/ :"
echo "    python3 enrollment.py"
echo "    python3 identification.py ../test_samples/<fichier>.wav"
