# Villages 1.1

Plugin compagnon de **Better Tree** (ex-Arbres) (Paper 26.3, Java 25). Même esprit : décors naturels, rien ne change les biomes,
aucun pack de textures à installer. Better Tree n'est pas obligatoire (`softdepend`), mais les deux plugins se complètent.

## Compiler
GitHub Actions (`.github/workflows/build.yml`) ou `mvn package` -> `target/Villages-1.1.jar`.
Mets le .jar dans `plugins/` à côté de BetterTree, supprime l'ancien `config.yml` s'il existe, redémarre.

## Ce que fait le plugin

**Villages (plaines, désert, savane, neige, taïga)** : améliorés une seule fois, à la génération du chunk.
- lampadaires (poteau + lanterne) espacés le long des chemins, et une lanterne près de chaque porte ;
- jardinets autour des maisons, selon le biome : fleurs, buissons, bûches, bancs, tonneaux, foin, pots (plaines) ;
  bûches, baies, bancs (taïga) ; cactus, piliers de grès, pots secs (désert) ; bonhommes de neige (neige) ; foin, pots secs (savane) ;
- chemins moins réguliers (terre tassée, gravier, bords usés) ;
- fondations de pierre sous les maisons qui flottent sur une pente.

**Structures vanilla** (l'intérieur, les pièges et les coffres ne sont jamais touchés) :
- pyramide du désert : dunes contre les murs, quatre obélisques (certains brisés), éboulis, cactus ;
- maison de sorcière : ponton en spruce avec rambardes, poteaux et lanternes, nénuphars, champignons ;
- igloo : congères, feu de camp avec souches, bonhomme de neige, pile de bois, lampadaires ;
- temple de la jungle : lianes, mousse, pierre moussue, buissons, piliers brisés aux coins.

**Nouvelles structures**
- **Tour de guet en bois** (taïga, taïga neigeuse, vieilles taïgas) : très rare. Deux étages fermés, échelle, plate-forme
  avec rambarde, toit en pyramide, coffre de ravitaillement. Les arbres qui gênent sont retirés en entier.
- **Maison champignon** (île champignon) : pied de champignon habitable sous un grand chapeau rouge (ou brun),
  mezzanine avec lit, échelle, fenêtres jaunes, lanternes, coffre de provisions.

## Commandes (permission `villages.admin`, op par défaut)
- `/villages convert [rayon]` : améliore les structures déjà générées autour de toi (rayon 1 à 20 chunks).
- `/villages stats` : nombre de structures améliorées et en attente.
- `/villages reload` : recharge `config.yml`.

## Notes
- Chaque structure n'est améliorée qu'une fois (liste dans `plugins/Villages/processed.txt`).
- Le travail est étalé (8 ms max par tick) ; un village peut mettre quelques secondes à se compléter après sa génération.
- Les structures sont lues via l'API Paper `GeneratedStructure`. Si elle est absente, le log le dit au démarrage ;
  la tour et la maison champignon marchent quand même.
- Réglages de rareté : `wooden-tower.chance` (0.30 par case de 12x12 chunks) et `mushroom-house.chance`.


## Changements 1.1
- Maisons de village : cheminées de pierre qui fument sur le faîte (`villages.chimneys`) et lierre sur les murs en plaines, savane et taïga (`villages.vines`). Seules les vraies maisons (avec une porte) sont touchées, pas les puits ni les champs.
- Maison champignon : taches claires sur le chapeau rouge, cheminée qui fume au sommet, deux lampadaires le long du chemin.
- Tour de guet : pieds posés sur des socles de pierre, fanion vert au sommet du toit.
