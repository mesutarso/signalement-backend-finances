# Intégration OCR Herdys

Le backend consomme l’API livrée dans `ocr-flask-coolify` sans modifier son code. Les opérations restent déclenchées par un agent ; aucun traitement automatique de l’historique n’est lancé.

## Configuration

Variables du **backend**, à renseigner dans Coolify :

```dotenv
HERDYS_OCR_BASE_URL=http://signalements-ocr:5000/ocr
HERDYS_OCR_API_TOKEN=<secret partagé avec API_TOKEN de Flask>
HERDYS_OCR_MODEL=mistral
HERDYS_OCR_TIMEOUT=200s
```

- L’URL est l’endpoint complet, avec `/ocr`. Utiliser HTTPS si le service est atteint hors du réseau privé du serveur.
- Le fournisseur est `mistral` ou `openai`, choisi côté serveur. Aucun basculement automatique n’est effectué.
- Le jeton est obligatoire dans notre connecteur. Sans URL ou jeton, une demande produit une lecture en échec `not-configured`, consultable et rejouable après configuration.
- Le délai de lecture est configurable, positif et au maximum de cinq minutes ; la connexion dispose d’un délai de cinq secondes. Le défaut de 200 secondes laisse une marge par rapport au timeout Gunicorn de 180 secondes fourni par Herdys. Les lenteurs, limites et quotas des fournisseurs doivent être mesurés en recette réelle.
- Les clés Mistral et OpenAI résident dans **Flask**. Elles ne sont pas envoyées par le navigateur ni nécessaires au connecteur Java Herdys.

Les exemples d’environnement et les deux fichiers Compose de la plateforme transmettent ces variables. Les secrets réels ne doivent pas être copiés dans ces exemples.

## Déploiement Coolify avec les dépôts séparés

1. Créer une troisième application depuis `https://github.com/mesutarso/signalement-ocr-finances-`, branche `main`, en utilisant son Dockerfile existant et le port interne 5000. Le remote `origin` du dossier OCR reste celui de Herdys ; le remote `prod` désigne ce dépôt de production.
2. La raccorder au même réseau externe `coolify` que le backend, avec l’alias réseau unique `signalements-ocr`. Vérifier cet alias depuis le conteneur backend ; un nom d’application Coolify ne garantit pas à lui seul cet alias.
3. Dans cette application Flask, définir `API_TOKEN`, `REQUIRE_API_TOKEN=true` et la clé du ou des fournisseurs retenus (`MISTRAL_API_KEY`, `OPENAI_API_KEY`). Garder l’accès sortant aux fournisseurs.
4. Le service OCR reste privé : aucun port hôte publié, aucun domaine public et aucun accès au réseau `data` contenant PostgreSQL et Redis. Pour la plateforme, le frontend est le seul service applicatif exposé sur Internet.
5. Renseigner les quatre variables du backend ci-dessus, puis redéployer le backend et le frontend modifiés.
6. Vérifier `/health` depuis le réseau privé, puis une lecture réelle sur une pièce de test autorisée. Le contrôle `/health` de Flask vérifie uniquement le processus ; il ne valide pas les clés ni les quotas fournisseurs.

Le README livré par Herdys demande la révocation d’anciennes clés. Ce point doit être confirmé avec lui avant la mise en service.

### Application OCR créée le 9 novembre 2026

- Projet `TOMBOLA`, environnement `production`, application `signalements-ocr`, UUID `etdhqrx1idilhzehin8wkpsl`.
- Source : remote `prod`, branche `main`, commit `8c98d75ad493f7df10ba16ba31c28c8a14355440`.
- Réseau Docker `coolify`, nom interne `signalements-ocr`, port interne `5000`.
- URL backend : `http://signalements-ocr:5000/ocr`. Aucun domaine ni port public ; la route publique initialement préparée a été retirée à la demande de l’utilisateur.
- La clé Mistral et un jeton OCR de production distinct du jeton local sont configurés dans Coolify. Les valeurs de référence sont conservées hors Git dans `ocr-flask-coolify/.env.production` ; aucune valeur ne doit être copiée dans la documentation.
- Le backend de production atteint `/health` par le réseau Docker privé : réponse `{"statut":"ok"}` vérifiée depuis son conteneur.
- Une lecture Mistral depuis le conteneur OCR privé a répondu en HTTP 200 en 2,8 secondes sur une facture générée en mémoire, entièrement fictive : TTC `116000.00 CDF`, HT `100000.00`, TVA `16000.00`, payé `120000.00`, numéro DEF `0000123` correctement extraits.
- Les diagnostics, la variable de recette et la dépendance de génération temporaire ont été retirés après le test. Le code Flask n’a pas été modifié.
- État final vérifié : `running:healthy`, aucun domaine ni port hôte publié, ancienne route publique en HTTP 404, frontend public en HTTPS 200. Voir [la preuve de recette](../deploy/ocr-verification-2026-11-09.json).
- Dans Coolify 4.6, laisser le contrôle HTTP du tableau de bord désactivé (`health_check_enabled=false`) pour utiliser le `HEALTHCHECK` Python du Dockerfile. Le contrôle natif est bien détecté (`custom_healthcheck_found=true`) et Docker surveille toujours l’application ; activer le contrôle HTTP généré remplace ce contrôle natif dans cette version. Aucun outil supplémentaire n’a été ajouté à l’image.

## Contrat et conservation

Le backend envoie un JSON `{"image":"<base64>","modele":"mistral"}` avec `Authorization: Bearer <jeton>`. Les images JPEG, PNG et WebP sont acceptées jusqu’à 5 Mio décodés. Les PDF sont refusés avant l’appel ; les pièces originales restent conservées.

Les six champs initiaux, les articles et la confiance restent disponibles. Les champs `devise`, `montant_ht`, `tva`, `taux_tva`, `montant_paye`, `code_def`, `numero_def` sont conservés et exposés comme `currency`, `netAmount`, `vatAmount`, `vatRate`, `paidAmount`, `defCode`, `defNumber`.

La table existante `extracted_field` accueille ces valeurs : aucune migration de structure n’est nécessaire. Les anciennes lectures restent consultables avec les nouveaux champs à null. La version enregistrée est `herdys-v2` ; le parcours enregistré est `herdys-mistral` ou `herdys-openai`. Flask ne retourne pas le modèle effectif ni la version de prompt : ces métadonnées ne peuvent pas être certifiées par le connecteur.

Une réponse contenant `erreur`, une enveloppe dépourvue des six clés initiales, une confiance hors de [0,1], des types invalides ou des articles trop longs est rejetée comme réponse inexploitable. Une extraction avec ces clés à null reste une lecture valide mais illisible.

L’extraction est une aide à l’examen humain. Elle ne vérifie pas FACNO/DGIREP, ne confirme pas de fraude et ne déclenche pas de points.

## Cache et relance

```http
POST /api/reports/{reportId}/photos/{photoId}/readings
POST /api/reports/{reportId}/photos/{photoId}/readings?refresh=true
```

Le premier appel réutilise un succès seulement si la photo, son empreinte, la version de schéma et le parcours fournisseur correspondent. Le second crée une nouvelle tentative même après un succès ; le bouton « Relancer » l’utilise. Les lectures précédentes restent en base et le dossier affiche la dernière tentative.

Les échecs peuvent être retentés. Deux lectures simultanées d’une même photo sont refusées par un HTTP 409 **dans la même instance backend**. Le déploiement documenté utilise une instance ; cette protection devra devenir distribuée avant plusieurs réplicas. La file persistante et la lecture automatique après import restent une étape distincte.

## Erreurs

Les erreurs conservées distinguent configuration absente, jeton refusé, document refusé, taille/type, quota, timeout, réponse invalide, service inaccessible et erreur amont. Aucun détail privé reçu du fournisseur n’est exposé aux agents.

Limites du contrat Flask inchangé : un mauvais jeton produit actuellement un retour incorrect pouvant devenir HTTP 500 ; notre backend l’affiche comme erreur amont, sans le présenter comme une lecture réussie. Certaines erreurs fournisseurs sont également ramenées à HTTP 400 ou 502 par Flask : le backend ne peut pas retrouver une cause que l’API n’a pas distinguée. Ces anomalies restent à transmettre à Herdys.

## Vérification reproductible

Les tests HTTP du connecteur simulent les réponses et les erreurs sans fournisseur réel. Le test du circuit vérifie le passage des champs jusqu’au stockage et au DTO de photo. La suite couvre aussi la relance, les limites, la concurrence locale et les droits HTTP.

```bash
./mvnw verify
```

Pour tester le client Java contre les routes de l’API Flask originale, installer les dépendances OCR dans un environnement Python de test puis lancer depuis `backend` :

```bash
python scripts/serve-herdys-contract.py ../ocr-flask-coolify/app.py
```

Dans un second terminal, depuis `backend` :

```bash
./mvnw -Dherdys.contract.url=http://127.0.0.1:15001/ocr verify
```

Le serveur de contrat charge le fichier Flask original, désactive le chargement de son `.env` et remplace uniquement les appels HTTP fournisseurs par des réponses synthétiques. Les deux parcours sont testés sans coût et sans secret réel. Sans ce serveur explicitement configuré, `HerdysFlaskContractTest` est ignoré ; les autres tests restent exécutés.

Validation du 9 novembre 2026 : **155 tests Java réussis**, incluant le contrat HTTP Flask simulé pour Mistral et OpenAI ; build backend et frontend réussis ; TypeScript et lint des quatre fichiers frontend modifiés réussis. Vérification navigateur avec données synthétiques : champs fiscaux affichés, relance explicite, message de quota et reprise après échec ; affichage mobile sans débordement horizontal. Le lint global relève huit erreurs et deux avertissements préexistants dans d’autres fichiers, hors de cette intégration.

Recette réelle locale complémentaire du 9 novembre 2026 : frontend, backend et Flask répondent en HTTP 200. Deux lectures d’une facture entièrement fictive avec la clé Mistral locale réussissent en environ 5 secondes chacune : une via Flask directement, l’autre via un dossier synthétique dans le backend. Total TTC, montant payé, TVA, devise et références DEF sont correctement extraits ; persistance PostgreSQL et affichage dans le frontend réel vérifiés. Une seconde demande sans relance réutilise le résultat en environ 0,02 seconde. Les requêtes base64 malformées et les fichiers sans signature d’image sont refusés en HTTP 400 et 415.

Le dossier synthétique et sa lecture sont retirés après recette. Aucun document utilisateur n’a été envoyé au fournisseur. Le code Flask livré reste inchangé ; ses variables locales ont été raccordées au backend, avec jeton partagé et port 5001 puisque le port 5000 du Mac est occupé. OpenAI n’est pas testé en réel faute de clé locale. Le réseau Coolify, les quotas en charge et la qualité sur un corpus représentatif restent à valider.
