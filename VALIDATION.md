# Validation des signalements et récompenses

La migration V44 introduit la grille approuvée le 12 novembre 2026. Un signalement doit concerner une facture non déjà signalée et satisfaire l’un des cas suivants :

- établissement assujetti à la TVA et facture non normalisée ;
- établissement non assujetti qui facture de la TVA.

Une information inconnue reste à vérifier. Le score OCR ne prouve ni l’assujettissement, ni la normalisation, ni l’achat réel. Les deux cas sont alternatifs. La conformité à l’achat réel reste une ancienne donnée conservée ; elle ne participe plus à la grille courante.

## Précalcul sans DGI-REP

L’absence d’accès à DGI-REP ne suspend ni les imports, ni l’extraction OCR, ni le précalcul. La réponse `GET /api/reports/{id}/assessment` contient `precalculation` : les observations disponibles pour chaque critère, les deux décisions possibles (`ifRegistered`, `ifUnregistered`) et l’indication `onlyFiscalStatusMissing` lorsque seul l’assujettissement manque pour conclure dans les deux cas. Une analyse encore en attente ne présente aucune conclusion acquise.

L’agent voit d’abord les critères préparés et renseigne l’assujettissement dans un formulaire dédié. `POST /api/reports/{id}/assessment/fiscal-status` reçoit uniquement `{ revision, vatStatus, vatEvidence }`, avec `vatStatus` égal à `registered` ou `unregistered`. Une source et sa référence sont requises. Le serveur conserve les données OCR, la pièce sélectionnée et tous les autres faits ; il enregistre l’auteur et la date, puis recalcule la décision et le droit à récompense dans la même transaction. Les révisions périmées et les analyses en cours sont refusées en HTTP 409. Les droits de consultation et la séparation des rôles restent applicables.

Le précalcul et la décision enregistrée utilisent le même moteur, y compris les doublons non résolus, les doublons confirmés et les factures déjà récompensées. Les deux prévisions sont des résultats conditionnels et ne créent aucun droit. L’agent ne choisit pas arbitrairement la décision finale : celle-ci découle des faits et du statut fiscal renseigné.

Les références DEF détectées sont présentées comme indices, sans inventer une normalisation vérifiée. L’absence d’alerte ne prouve pas l’unicité de la facture. Le précalcul signale les éléments encore inconnus et le formulaire de correction reste disponible. Aucun appel DGI-REP n’est nécessaire pour cette préparation.

## Informations incomplètes et nouvelle photo

La règle `invoice-completion-2026-11-19` conserve les dossiers illisibles ou incomplets dans `doubt`, affichés « À compléter ». Le précalcul fournit `missingFields` (photo lisible, NIF, numéro, date). L’absence d’une référence DEF ne suffit pas à prouver une facture non normalisée. Un incident du fournisseur de lecture n’est pas une preuve d’illisibilité.

L’agent peut corriger le NIF, le numéro, la date et la normalisation depuis la fiche. Une correction du NIF remet la confirmation fiscale à vérifier. L’assujettissement exige une référence de vérification ; l’auteur et la date sont conservés.

`POST /api/reports/{id}/photos/{photoId}/requests` prépare une demande et un SMS de 100 caractères maximum. Une demande ouverte par pièce est conservée, y compris en cas de double clic. L’envoi explicite utilise `/api/reports/{id}/photo-requests/{requestId}/deliveries`, autorisé à l’agent pour cette seule demande et le numéro du déclarant enregistré. Aucun destinataire ni contenu libre n’est accepté. Les états d’envoi existants empêchent un second envoi accepté ou incertain. Les anciens brouillons photo sans demande restent bloqués.

Le rattachement `/api/reports/{id}/photo-requests/{requestId}/links` vérifie que le nouveau signalement appartient au même déclarant, conserve les originaux, copie les nouvelles pièces dans le dossier initial, clôture la demande et relance l’analyse. Le signalement de remplacement est exclu comme deuxième demande de récompense. Une relance terminée quitte le filtre des demandes ouvertes ; sa trace reste consultable. Une nouvelle relance dispose de son propre message.

La migration V49 ajoute la relation entre demande et message et des index. Les écritures existantes sont conservées. Le mode SMS de production reste inchangé ; les tests locaux utilisent uniquement un simulateur.

## File et fiche agent

La recherche porte également sur établissement, NIF et numéro de facture. Les filtres par période, agent, statut TVA, lecture, informations manquantes, doublons et relance sont appliqués côté serveur avant pagination. Le tri est global. La fiche conserve le contexte de recherche pour le retour, le précédent et le suivant. Elle affiche les éléments manquants, les critères et les deux prévisions fiscales avec les actions de correction et de relance.

## Circuit de traitement

L’import crée un travail durable dans la même transaction que le dossier et ses pièces. Le traitement extrait les données, prépare les rapprochements puis applique la règle. Les états sont `queued`, `analyzing`, `doubt`, `validated`, `rejected` et `legacy`.

Le rôle AGENT travaille par défaut sur les dossiers `doubt`. Il peut consulter les autres dossiers courants ; leurs modifications restent réservées aux rôles autorisés. Les superviseurs et administrateurs gardent la vue globale ; les auditeurs disposent de la consultation. Le changement d’état est indépendant de l’affectation du dossier.

En attendant les accès DGI-REP, l’agent confirme l’assujettissement à partir d’une source fiscale identifiée et saisit sa référence. L’auteur et la date du contrôle sont enregistrés. La normalisation est renseignée par l’agent à partir de la facture. Une photo déclarée illisible reste à compléter ; les incidents de lecture restent à vérifier.

`FiscalRegistry` constitue le point de raccordement prévu pour DGI-REP. L’implémentation actuelle retourne « indisponible » : aucun appel DGI-REP ni simulation d’assujettissement n’est utilisé. Le contrôle fiscal de l’agent remplace temporairement cette source, sans arrêter le traitement automatique en amont.

Chaque décision conserve la version de règle, la révision, les preuves et les références d’extraction. Les écrans et les API de dossier exposent uniquement la vérification courante, sans panneaux de décisions ou d’affectations historiques. `/api/reward-settings` retourne un seul objet : le montant actif. L’ancienne saisie `/api/reports/{id}/qualifications` est supprimée. Un formulaire obsolète reçoit HTTP 409. Les alertes concernant la même paire de dossiers sont tranchées ensemble ; le dossier perdant d’un doublon confirmé ne peut pas être récompensé.

Les travaux ont un bail de dix minutes renouvelable et un identifiant de propriétaire. Un ancien traitement ne peut pas écraser un traitement repris. Les échecs sont réessayés trois fois avec une minute d’attente ; ensuite le dossier passe à l’agent. Une nouvelle pièce invalide les faits précédents et programme une nouvelle analyse. Une relance seule conserve les faits vérifiés. `REPORT_ANALYSIS_ENABLED=false` suspend le traitement sans supprimer la file.

## Récompense

Le montant initial est de **5 000 FC par signalement validé**, modifiable par un superviseur ou administrateur depuis `/regles`. Chaque modification crée une version et s’applique aux récompenses créées ensuite. Une revalidation conserve l’identifiant et le montant initiaux.

Les contraintes de base empêchent plusieurs récompenses pour un même dossier ou une même clé de facture. Cette clé utilise le vendeur/NIF, le numéro et la date, ou l’empreinte de la pièce si le numéro manque. Une collision demande une vérification ; elle ne suffit pas à déclarer deux factures identiques. Le contrôle d’antériorité reste requis, notamment lorsque les références sont absentes ou corrigées.

La validation crée un droit proposé, sans effectuer de paiement. Les contrôles existants d’identité du bénéficiaire, de plafond cumulé, de budget, de séparation des rôles et de visa restent applicables. Le plafond de bénéficiaire continue de provenir du paramétrage existant. Un dossier sans identifiant déclarant ou téléphone conserve son droit, mais doit être complété avant paiement.

Une correction retire la récompense non payée et bloque les états de paiement qui la contiennent. Un paiement déjà enregistré reste dans l’historique. Les validations, corrections et opérations de paiement sont sérialisées pour éviter un paiement concurrent à une invalidation.

## Reprise de l’existant

Pour une relecture OCR réelle du stock déjà importé, définir `REPORT_ANALYSIS_REPLAY_BEFORE` à un instant ISO-8601 fixe. Toutes les dix secondes, au plus 25 dossiers avec pièces et analyse terminée avant cet instant rejoignent la file durable. Leur date de mise à jour est contrôlée de nouveau sous le verrou métier ; un même dossier n’est pas remis en file en boucle. Les nouveaux imports continuent leur circuit normal. Aucun schéma ni service supplémentaire n’est nécessaire.

Le traitement force une nouvelle extraction lorsque la dernière lecture réussie précède cet instant. Une photo déjà relue avec succès depuis cet instant est réutilisée lors d’une reprise ou d’un redémarrage. Les anciennes lectures restent conservées. Les montants de TVA issus automatiquement de l’OCR sont actualisés ; une vérification humaine justifiée est conservée. Les tentatives restent bornées par le mécanisme existant.

Les dossiers historiques `legacy`, les récompenses réellement payées et les versements réels en cours ou incertains sont exclus de cette reprise. Les vérifications fiscales saisies ne sont pas réinitialisées. L’instant de reprise peut être retiré de la configuration une fois le lot terminé. L’écran actualise automatiquement les dossiers dont l’analyse est en cours et ne propose plus de lecture manuelle par photo.

Les dossiers ayant déjà une qualification ou des mouvements de points sont marqués `legacy`. Leurs décisions restent consultables. Les dossiers sans décision entrent dans la nouvelle file. Les modules de points, les primes par paliers et leur historique sont retirés des écrans et des API ; aucune conversion ni nouvelle opération sur ces anciens droits n’est effectuée. Seules les récompenses par signalement sont listées et admises dans le circuit de paiement. Les anciennes écritures de base et les paiements déjà exécutés ne sont pas effacés. Les routes `/points`, `/primes`, `/api/points`, `/api/primes` et l’ancien barème `/api/rules` sont supprimés ; le circuit courant utilise `/recompenses` et `/api/rewards`.

## Vérification

`./mvnw clean verify` exécute les tests Java. Pour tester réellement l’évolution V43 → V44, les contraintes et les reprises de bail, fournir `VALIDATION_JDBC_URL`, `VALIDATION_DB_USER` et `VALIDATION_DB_PASSWORD`. Le compte doit pouvoir créer une base de recette isolée ; ne pas utiliser un serveur de production.

`scripts/recette_validation.py` vérifie les parcours HTTP sur des données synthétiques avec un simulateur OCR/FACNO local. Il nécessite les conteneurs de recette PostgreSQL/Redis et un fichier privé `--runtime` décrit dans son en-tête. Il ne contacte aucun fournisseur et ne réalise aucun paiement réel. Les mots de passe de recette ne doivent pas être ajoutés au dépôt.

Recette locale du 15 novembre 2026 : 168 tests Java recensés, dont 165 réussis et trois tests de fournisseurs externes ignorés ; les tests PostgreSQL de migration et de concurrence sont activés sur un serveur éphémère. Les 33 contrôles HTTP réussissent, incluant le précalcul sans DGI-REP, la conservation des données lors de la saisie fiscale, les décisions différentes selon l’assujettissement, le rejet des photos illisibles et le refus des anciennes demandes et brouillons de remplacement. Build frontend, TypeScript et lint des fichiers modifiés réussis. Parcours agent vérifié dans Next.js en développement, sans erreur de compilation ni d’exécution ; affichage à 390 px sans débordement. Cette recette est synthétique et ne constitue pas un déploiement de production.

## Recette du parcours de complétion

`./mvnw clean verify` couvre les tests Java ; les variables `VALIDATION_JDBC_URL`, `VALIDATION_DB_USER`, `VALIDATION_DB_PASSWORD` activent les tests sur des bases PostgreSQL éphémères. `scripts/recette_completion.py --runtime /chemin/prive/runtime.json --keep-running` vérifie le parcours HTTP avec FACNO, OCR et SMS locaux simulés. Aucun SMS, import ou paiement réel n’est exécuté. Le fichier privé et les conteneurs suivent le format décrit par le script de recette de validation.
