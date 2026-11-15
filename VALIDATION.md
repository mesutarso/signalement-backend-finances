# Validation des signalements et récompenses

La migration V44 introduit la grille approuvée le 12 novembre 2026. Un signalement doit concerner une facture non déjà signalée et satisfaire l’un des cas suivants :

- établissement assujetti à la TVA et facture non normalisée ;
- établissement assujetti à la TVA et montant ou articles non conformes à l’achat réel ;
- établissement non assujetti qui facture de la TVA.

Une information inconnue reste à vérifier. Le score OCR ne prouve ni l’assujettissement, ni la normalisation, ni l’achat réel. Les critères sont alternatifs selon le cas : ils ne peuvent pas tous être vrais simultanément.

## Précalcul sans DGI-REP

L’absence d’accès à DGI-REP ne suspend ni les imports, ni l’extraction OCR, ni le précalcul. La réponse `GET /api/reports/{id}/assessment` contient `precalculation` : les observations disponibles pour chaque critère, les deux décisions possibles (`ifRegistered`, `ifUnregistered`) et l’indication `onlyFiscalStatusMissing` lorsque seul l’assujettissement manque pour conclure dans les deux cas. Une analyse encore en attente ne présente aucune conclusion acquise.

L’agent voit d’abord les critères préparés et renseigne l’assujettissement dans un formulaire dédié. `POST /api/reports/{id}/assessment/fiscal-status` reçoit uniquement `{ revision, vatStatus, vatEvidence }`, avec `vatStatus` égal à `registered` ou `unregistered`. Une source et sa référence sont requises. Le serveur conserve les données OCR, la pièce sélectionnée et tous les autres faits ; il enregistre l’auteur et la date, puis recalcule la décision et le droit à récompense dans la même transaction. Les révisions périmées et les analyses en cours sont refusées en HTTP 409. Les droits de consultation et la séparation des rôles restent applicables.

Le précalcul et la décision enregistrée utilisent le même moteur, y compris les doublons non résolus, les doublons confirmés et les factures déjà récompensées. Les deux prévisions sont des résultats conditionnels et ne créent aucun droit. L’agent ne choisit pas arbitrairement la décision finale : celle-ci découle des faits et du statut fiscal renseigné.

Les références DEF détectées sont présentées comme indices, sans inventer une normalisation vérifiée. La conformité à l’achat réel nécessite des éléments comparatifs ; l’absence d’alerte ne prouve pas l’unicité de la facture. Si ces éléments restent inconnus, le précalcul l’indique pour la branche concernée et le formulaire de correction reste disponible. Aucun appel DGI-REP n’est nécessaire pour cette préparation.

## Photo illisible : rejet sans demande de remplacement

La version de règle `criteria-readable-2026-11-15` rejette une photo de référence dont une extraction réussie retourne `UNREADABLE`, même sans statut fiscal. Si aucune pièce n’est encore sélectionnée et que toutes les pièces sont déclarées illisibles après extraction réussie, le dossier est également rejeté. Une pièce illisible parmi plusieurs ne suffit pas à rejeter les autres pièces lisibles.

Les erreurs de fournisseur, quotas, délais dépassés et formats non pris en charge ne sont pas assimilés à une photo illisible. Une lecture `PARTIAL` n’établit pas non plus l’illisibilité : elle peut correspondre à un élément réellement absent sur la facture. Ces cas restent à vérifier, ou sont repris selon le mécanisme existant.

Les nouvelles demandes de photo sont désactivées : l’ancien endpoint répond HTTP 410, le bouton est retiré, et aucun message de remplacement n’est créé. La préparation et l’envoi des messages de type `photo`, y compris des anciens brouillons, sont refusés. Les anciennes demandes et les messages déjà enregistrés restent consultables.

## Circuit de traitement

L’import crée un travail durable dans la même transaction que le dossier et ses pièces. Le traitement extrait les données, prépare les rapprochements puis applique la règle. Les états sont `queued`, `analyzing`, `doubt`, `validated`, `rejected` et `legacy`.

Le rôle AGENT consulte uniquement les dossiers `doubt`, y compris pour les liens directs vers les pièces. Les superviseurs et administrateurs gardent la vue globale ; les auditeurs disposent de la consultation. Le changement d’état est indépendant de l’affectation du dossier.

En attendant les accès DGI-REP, l’agent confirme l’assujettissement à partir d’une source fiscale identifiée et saisit sa référence. L’auteur et la date du contrôle sont enregistrés. La normalisation et la comparaison avec l’achat réel exigent aussi des justificatifs lorsqu’elles sont renseignées. Une photo déclarée illisible est rejetée selon la règle ci-dessus ; les incidents techniques OCR restent à vérifier.

`FiscalRegistry` constitue le point de raccordement prévu pour DGI-REP. L’implémentation actuelle retourne « indisponible » : aucun appel DGI-REP ni simulation d’assujettissement n’est utilisé. Le contrôle fiscal de l’agent remplace temporairement cette source, sans arrêter le traitement automatique en amont.

Chaque décision conserve la version de règle, la révision, les preuves et les références d’extraction. Les écrans et les API de dossier exposent uniquement la vérification courante, sans panneaux de décisions ou d’affectations historiques. `/api/reward-settings` retourne un seul objet : le montant actif. L’ancienne saisie `/api/reports/{id}/qualifications` est supprimée. Un formulaire obsolète reçoit HTTP 409. Les alertes concernant la même paire de dossiers sont tranchées ensemble ; le dossier perdant d’un doublon confirmé ne peut pas être récompensé.

Les travaux ont un bail de dix minutes renouvelable et un identifiant de propriétaire. Un ancien traitement ne peut pas écraser un traitement repris. Les échecs sont réessayés trois fois avec une minute d’attente ; ensuite le dossier passe à l’agent. Une nouvelle pièce invalide les faits précédents et programme une nouvelle analyse. Une relance seule conserve les faits vérifiés. `REPORT_ANALYSIS_ENABLED=false` suspend le traitement sans supprimer la file.

## Récompense

Le montant initial est de **5 000 FC par signalement validé**, modifiable par un superviseur ou administrateur depuis `/regles`. Chaque modification crée une version et s’applique aux récompenses créées ensuite. Une revalidation conserve l’identifiant et le montant initiaux.

Les contraintes de base empêchent plusieurs récompenses pour un même dossier ou une même clé de facture. Cette clé utilise le vendeur/NIF, le numéro et la date, ou l’empreinte de la pièce si le numéro manque. Une collision demande une vérification ; elle ne suffit pas à déclarer deux factures identiques. Le contrôle d’antériorité reste requis, notamment lorsque les références sont absentes ou corrigées.

La validation crée un droit proposé, sans effectuer de paiement. Les contrôles existants d’identité du bénéficiaire, de plafond cumulé, de budget, de séparation des rôles et de visa restent applicables. Le plafond de bénéficiaire continue de provenir du paramétrage existant. Un dossier sans identifiant déclarant ou téléphone conserve son droit, mais doit être complété avant paiement.

Une correction retire la récompense non payée et bloque les états de paiement qui la contiennent. Un paiement déjà enregistré reste dans l’historique. Les validations, corrections et opérations de paiement sont sérialisées pour éviter un paiement concurrent à une invalidation.

## Reprise de l’existant

Les dossiers ayant déjà une qualification ou des mouvements de points sont marqués `legacy`. Leurs décisions restent consultables. Les dossiers sans décision entrent dans la nouvelle file. Les modules de points, les primes par paliers et leur historique sont retirés des écrans et des API ; aucune conversion ni nouvelle opération sur ces anciens droits n’est effectuée. Seules les récompenses par signalement sont listées et admises dans le circuit de paiement. Les anciennes écritures de base et les paiements déjà exécutés ne sont pas effacés. Les routes `/points`, `/primes`, `/api/points`, `/api/primes` et l’ancien barème `/api/rules` sont supprimés ; le circuit courant utilise `/recompenses` et `/api/rewards`.

## Vérification

`./mvnw clean verify` exécute les tests Java. Pour tester réellement l’évolution V43 → V44, les contraintes et les reprises de bail, fournir `VALIDATION_JDBC_URL`, `VALIDATION_DB_USER` et `VALIDATION_DB_PASSWORD`. Le compte doit pouvoir créer une base de recette isolée ; ne pas utiliser un serveur de production.

`scripts/recette_validation.py` vérifie les parcours HTTP sur des données synthétiques avec un simulateur OCR/FACNO local. Il nécessite les conteneurs de recette PostgreSQL/Redis et un fichier privé `--runtime` décrit dans son en-tête. Il ne contacte aucun fournisseur et ne réalise aucun paiement réel. Les mots de passe de recette ne doivent pas être ajoutés au dépôt.

Recette locale du 15 novembre 2026 : 168 tests Java recensés, dont 165 réussis et trois tests de fournisseurs externes ignorés ; les tests PostgreSQL de migration et de concurrence sont activés sur un serveur éphémère. Les 33 contrôles HTTP réussissent, incluant le précalcul sans DGI-REP, la conservation des données lors de la saisie fiscale, les décisions différentes selon l’assujettissement, le rejet des photos illisibles et le refus des anciennes demandes et brouillons de remplacement. Build frontend, TypeScript et lint des fichiers modifiés réussis. Parcours agent vérifié dans Next.js en développement, sans erreur de compilation ni d’exécution ; affichage à 390 px sans débordement. Cette recette est synthétique et ne constitue pas un déploiement de production.
