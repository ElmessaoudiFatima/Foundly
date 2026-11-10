# Guide de test manuel — Espace Entrepreneur

## Préparation

1. Démarrer MySQL avec la base `foundly-db` configurée dans `backend/src/main/resources/application.properties`.
2. Depuis `backend`, lancer `./mvnw.cmd spring-boot:run` sous PowerShell.
3. Depuis `frontend`, lancer `npm start`, puis ouvrir `http://localhost:4200`.
4. Créer un nouveau compte entrepreneur depuis **Inscription** pour tester un dossier vierge.

Les comptes de démonstration sont créés uniquement lorsque la table des dossiers est vide. Dans ce cas, leur mot de passe est `Pass123456!`. Par exemple : `aya.mansouri@gmail.com` possède un dossier SARL en cours ; `youssef.amrani@gmail.com` possède un dossier en correction.

## Tests de création et règles métier

| Cas | Action | Résultat attendu |
|---|---|---|
| Auto-entrepreneur | Créer un dossier, choisir **Auto-entrepreneur**. | Le wizard affiche 4 étapes simplifiées ; aucun champ capital ; aucune mention de certificat négatif, statuts ou Registre de Commerce. |
| SARL-AU / SARL, capital ≤ 100 000 DH | Créer le dossier avec 100 000 DH ou moins. | Le preview présente 8 étapes. La checklist rend l'attestation de blocage facultative et l'étape 3 est dispensée. |
| SARL-AU / SARL, capital > 100 000 DH | Créer le dossier avec par exemple 150 000 DH. | Le preview présente 8 étapes et la checklist exige l'attestation de blocage. |
| SA | Créer une SA. | Le preview présente 8 étapes et annonce que le blocage est obligatoire. |
| Modification | Ouvrir un brouillon, choisir **Modifier**. | Les informations sont sauvegardées ; la forme juridique reste modifiable seulement en brouillon. |

## Tests documentaires

1. Ouvrir un brouillon puis vérifier que la checklist dépend bien de la forme choisie.
2. Pour une société, le bouton **Générer** apparaît directement dans la ligne des statuts ou du formulaire CRI, uniquement tant que la pièce correspondante manque. Il crée un modèle PDF pré-rempli à télécharger, qui doit être vérifié, signé si nécessaire, puis téléversé dans cette même ligne. Un modèle généré seul ne valide jamais une pièce obligatoire.
3. Pour un auto-entrepreneur, vérifier qu'aucun bouton de génération Statuts/CRI n'est proposé : ces modèles ne sont pas applicables à son parcours RNAE.
4. Choisir **Déposer** ou déposer par glisser-déposer un PDF, JPG ou PNG inférieur à 5 Mo : le document doit apparaître avec son nom et les actions télécharger/supprimer.
5. Essayer un fichier `.txt`, puis un fichier de plus de 5 Mo : le front doit le refuser avant envoi. Un PNG valide doit être accepté.
6. Pour une SARL/SARL-AU > 100 000 DH ou une SA, déposer `ATTESTATION_BLOCAGE_CAPITAL`. La checklist doit devenir complète quand toutes les autres pièces sont présentes.
7. Cliquer sur l'icône de suppression d'un document : une modale Foundly doit demander confirmation. Cliquer **Conserver le document** ne change rien ; cliquer **Oui, supprimer** retire la pièce de la checklist.

## Test du paiement et de la soumission

1. Sur le premier jalon payé, utiliser **Payer les frais**.
2. Utiliser une carte de démonstration, par exemple `4111 1111 1111 1111`, titulaire `TEST ENTREPRENEUR`, expiration `12/30`, CVV `123`.
3. Confirmer : le paiement passe à **Confirmé** et le bouton **Reçu PDF** devient disponible.
4. Tenter de soumettre sans document obligatoire : la soumission est refusée et les pièces manquantes sont indiquées.
5. Tenter de soumettre une société sans le paiement du certificat négatif : l'interface ouvre le paiement et l'API doit refuser la soumission tant qu'il n'est pas confirmé (RG9).
6. Quand checklist et paiement sont complets, cliquer **Soumettre le dossier**. Le statut devient `SOUMIS`, une notification et un événement d'audit sont ajoutés.

## Tests de consultation

1. Revenir à **Mes dossiers** : vérifier badges, progression, forme juridique, étape courante, filtre de statut et pagination.
2. Ouvrir un dossier : vérifier timeline des étapes, capital, règles de blocage, paiements et historique d'audit.
3. Ouvrir la cloche : vérifier compteur non lu, lecture d'une notification, **Tout lire** et navigation vers le dossier lié.
4. Avec le compte de démonstration en correction, vérifier l'alerte, le motif et le bouton **Envoyer mes corrections** après remplacement de la pièce demandée.

## Limite actuelle à connaître

La finalisation complète après soumission requiert qu'un agent valide ou rejette chaque étape. Le backend contient le moteur de transition (`WorkflowEngineService`) mais ne fournit pas encore les routes `/agent/...` de file d'attente, validation et rejet. Par conséquent, le parcours Entrepreneur est testable jusqu'à la soumission et à la consultation, mais la progression administrative complète et le statut `FINALISE` ne peuvent pas être produits manuellement depuis l'interface tant que cet espace Agent/API n'est pas implémenté.

Les dossiers de démonstration permettent néanmoins de vérifier visuellement les états `EN_COURS`, `A_CORRIGER` et `FINALISE`.
