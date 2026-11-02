# SUIVI — Vrais bons plans (app compagnon idealo)

## 🧭 Ligne directrice
_MAJ 2026-11-01_ — ✅ Alerte multi-sources fonctionnelle + écran lisible en thème sombre, bouton « Vérifier » avec chargement, recherche et filtres (prix min/max, sources, tri). Prochaine priorité : retour de Floran sur les filtres, puis fonction « Prix de revente » (Vinted / Leboncoin / eBay).

## ✅ Fait
- [x] Projet Gradle (template AlcoLimit : AGP 9.1.1, Kotlin 2.1.20, Compose BOM 2025.01, WorkManager)
- [x] `IdealoWebSource` : WebView invisible → idealo.fr/bons-plans → fetch des endpoints `price-chart` depuis la page
- [x] Test premier plan : 72 bons plans listés, 5/5 historiques lus en ~4 s
- [x] Test arrière-plan (WorkManager, écran allumé, app vivante) : OK
- [x] Sources validées : **Dealabs** RSS `dealabs.com/rss/groupe/erreur-de-prix` (HTTP simple, 30 items, `pepper:merchant` name+price) ; **Ledenicheur** recherche `ledenicheur.fr/search?query=` (HTML) + historique `POST /api/price-history` `{productId, timeRange:"THREE_MONTHS", shopIds:[], variantIds:[]}` (HTTP simple, prix par marchand en centimes)
- [x] Test app tuée par HyperOS : OK après « Démarrage automatique » activé (relance auto 18:32)
- [x] Test veille profonde (`dumpsys battery unplug` + `deviceidle force-idle`, app tuée) : job lancé ~2 min après l'échéance, réseau OK
- [x] `RealDiscountCalculator` : prix habituel 30 j (médiane pondérée), pics filtrés vs 1er quartile puis creux vs 3e quartile, plus bas 3 mois, détection « historique incohérent » — 10 tests
- [x] `ProductNameMatcher` (recoupement de titres entre sites) — 4 tests ; `DealabsSource.parse` — 3 tests
- [x] Sources : `IdealoSource` (tri `?sortKey=biggestSaving`, 40 produits), `DealabsSource` (RSS, < 48 h), `LedenicheurSource` (toutes les recherches d'un scan dans une seule WebView) via `HiddenBrowser`
- [x] `DealScanner` : vrai bon plan = réel ≥ seuil + plus bas 3 mois ; ≥ 50 % → confirmation Ledenicheur obligatoire ; Ledenicheur qui contredit → fausse promo ; sinon « À vérifier » (pas de notif)
- [x] `ScanWorker` périodique (1 h, 15 min si seuil ≥ 75 %) + notifications (5 max + résumé) + « Ignorer ce produit »
- [x] Écran principal Compose : réglages, aide HyperOS (batterie + démarrage auto), vrais bons plans / à vérifier / fausses promos
- [x] Texte noir illisible en thème sombre corrigé (écran dans une `Surface`), couleurs sombres sans violet
- [x] Bouton « Vérifier » : chargement immédiat (roue + barre orange + « environ 1 min »)
- [x] Recherche + filtres épinglés en haut (`DealFilter`) : prix min/max, sources, tri — 5 tests (filtre « réduction min » retiré : doublon avec le seuil des alertes)
- [x] Dépôt GitHub privé : https://github.com/Floran-grmxx/VraisBonsPlans (branche main)
- [x] Maquette multi-sources (`../idealo_patch/prototype/deal_alert.html`)

## ⏳ Reste à faire
- [ ] Retour de Floran sur la recherche et les filtres (tap adb impossible : option sécurité USB désactivée)
- [ ] Dealabs : vérifier une vraie erreur de prix récente (aucune < 48 h lors des tests)
- [ ] Revente : WebView Vinted / Leboncoin / eBay + filtre accessoires + boutons « Ouvrir dans l'app »
- [ ] Ouvrir la fiche dans l'app idealo officielle (lien `https://www.idealo.fr/prix/{id}/…`)
- [ ] Recevoir un produit partagé depuis idealo (intent SEND)
- [ ] « Moins cher ailleurs » (clé SerpApi à fournir)

## 📋 Notes / gotchas
- **Pourquoi une app compagnon** : idealo est protégé par DexProtector, impossible à patcher (voir `../idealo_patch/SUIVI.md`).
- idealo.fr renvoie **403** à tout client HTTP non navigateur (anti-bot Akamai) → passer par une WebView.
- La page se recharge 1 à 2 fois juste après le 1er chargement (Akamai) → lancer l'extraction sur le **dernier** `onPageFinished` (compteur de génération).
- **ERREUR** `TimeoutCancellationException: Timed out waiting for 90000 ms` — cause : `View.postDelayed` sur une WebView jamais attachée à une fenêtre ne s'exécute jamais. Fix : `Handler(Looper.getMainLooper()).postDelayed`.
- Erreurs JS invisibles si on filtre la console → logger aussi les messages de niveau ERROR.
- adb sans « Débogage USB (paramètres de sécurité) » : pas de `input`, `pm grant`, `pm clear` → extra `autorun` pour lancer le test sans toucher l'écran. `am start` sur une activité déjà ouverte ne relance pas `onCreate` → `am force-stop` avant.
- Tâches WorkManager d'anciens lancements restent en file → `cancelAllWork()` avant un nouveau test.
- Après `am kill`, le job WorkManager reste « Ready: true » mais ne démarre jamais (22 min) même en bucket ACTIVE → blocage HyperOS « Démarrage automatique ». Avant : bucket **RARE** (app peu utilisée) → `am set-standby-bucket <pkg> active` pour tester.
- **HyperOS « Démarrage automatique »** : introuvable dans les réglages de l'app sur HyperOS OS1.0 → app Sécurité, activité `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity` (appop `MIUIOP(10008)` passe de `ignore` à `allow`). L'app finale doit ouvrir cet écran (avec repli sur les réglages de l'app).
- Exemption batterie : `dumpsys deviceidle whitelist +<pkg>` (fait en test) ; dans l'app → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- adb a perdu le téléphone une fois (« no devices/emulators found ») en plein test, revenu seul.
- Ledenicheur `/deals` = 404 en France (pas de liste de baisses) → sert uniquement au recoupement.
- **Ledenicheur renvoie 403 au client HTTP Android** (même avec `Accept: */*`), alors que curl depuis le PC passe → passer aussi par la WebView.
- idealo `bargain` renvoie parfois un corps vide → lire en `text()` + `JSON.parse` dans un try, produit par produit.
- Fausses grosses promos idealo : prix absurde **pendant des semaines** (TV LG 3 400 € 2 mois → 599 €, radiateur EDM 1 000 € 2 mois → 80 €) → la médiane elle-même est gonflée. Filtre des pics vs 1er quartile + exigence d'une 2e source au-delà de -50 %.
- Limite connue : quand Ledenicheur est gonflé aussi (coffret Pokémon 272 € « habituel »), la fausse promo passe.
- `am force-stop` efface les notifications de l'app → ne pas conclure « pas de notif » après un force-stop.
- Bug corrigé : les bons plans étaient marqués « notifiés » même quand la notification n'était pas affichée.
- Écrire les scripts d'édition Python dans un fichier (scratchpad) : un heredoc bash contenant des `'` a échoué (« unexpected EOF »).
- **Texte noir sur fond sombre** : un `LazyColumn` hors `Surface` garde la couleur de contenu par défaut (noir) → envelopper l'écran dans `Surface(color = background)`.
- Recherche : ne pas réutiliser `ProductNameMatcher.tokens` (il supprime les mots d'1 caractère → « ryzen 5 » ne trouvait rien) ; `DealFilter` a son propre découpage.
- Kotlin écrit via script Python : doubler les antislashs dans le .py (`\\\\p{M}`) pour obtenir `"\\p{M}"` en Kotlin (« Unsupported escape sequence » sinon).
- Copier `settings.gradle.kts` depuis AlcoLimit plutôt que via heredoc bash (les `\\` du regex sont mangés).
