# Cartographie Topologique Multi-Clusters D3.js (Interactive Fleet Topology)

Ce document décrit l'architecture, la modélisation mathématique et l'expérience utilisateur de la **Cartographie Topologique Multi-Clusters D3.js** intégrée dans l'OpenShift Operations Portal.

---

## 1. Vision & Objectifs Métier

Dans un écosystème hybride multi-cloud et multi-clusters OpenShift, les équipes d'infrastructure (SRE, Platform Engineers) et les responsables financiers (FinOps, IT Finance) souffrent d'une vision fragmentée :
- Les **Hubs ACM** gèrent les clusters mais masquent souvent le placement matériel physique sous-jacent.
- L'**inventaire CMDB** (vCenter, Nutanix, AWS) connaît les serveurs physiques et VMs mais ignore les projets applicatifs Kubernetes.
- Le **moteur FinOps** calcule les gaspillages par namespace mais sans corrélation directe avec les hyperviseurs hôtes.

La **Cartographie Topologique D3** réunit ces 4 mondes dans un **graphe de force interactif vivant**, offrant à la fois un **effet visuel percutant** et un outil d'investigation en temps réel.

```
       +---------------------------------------------+
       |           ACM Hubs (Hub Principal EU/US)    |  <-- Ring Pulse Violet
       +---------------------------------------------+
                              |
       +---------------------------------------------+
       |       Clusters OpenShift Managés            |  <-- Bleu (Prod), Ambre (Stage)
       +---------------------------------------------+
                              |
       +---------------------------------------------+
       |   Noeuds Hyperviseurs / Workers & Masters   |  <-- Slate (W/M Badges)
       +---------------------------------------------+
                              |
       +---------------------------------------------+
       |       Namespaces Applicatifs FinOps         |  <-- Vert (A/B), Rouge (D/F Waste)
       +---------------------------------------------+
```

---

## 2. Arborescence Topologique & Modélisation des Nœuds

Le graphe connecte de manière orientée et pondérée quatre strates d'infrastructure :

| Niveau | Type de Nœud | Rayon Visualisation | Couleur / Halo | Métadonnées Explicatives |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 0** | `HUB` | 22 px (Halo double) | Violet `#7828C8` / Aura `#C084FC` | Nombre de clusters administrés, URL d'API ACM, statut de synchronisation. |
| **Tier 1** | `CLUSTER` | 16 px (Halo simple) | Bleu `#006FEE` (Prod), Ambre `#F5A524` (Staging) | Version OCP, Région/Cloud, compte de nœuds, état de santé global. |
| **Tier 2** | `NODE` | 10 px | Ardoise sombre `#334155` (Worker), `#475569` (Master) | Rôle K8s (`W` ou `M`), cœurs logiques vCPU, mémoire RAM, hôte physique. |
| **Tier 3** | `NAMESPACE` | 9 px (Pointilleux) | Émeraude `#17C964` (A/B) ou Carmin `#F31260` (D/F) | Grade d'efficience, coût mensuel (€), équipe propriétaire, CPU/RAM gaspillés. |

---

## 3. Moteur Graphique D3.js (Force-Directed Physics Simulation)

La disposition dynamique du graphe repose sur l'algorithme de simulation physique `d3.forceSimulation` calibré pour éviter tout chevauchement tout en préservant la clarté hiérarchique :

### A. Répulsion Gravitationnelle (`d3.forceManyBody`)
Pour éviter l'accumulation au centre, chaque catégorie de nœud possède une masse et une force de répulsion gravitationnelle adaptée :
- **Hub ACM** : Force de `-800` (repousse fortement pour se positionner comme centre de gravité régional).
- **Cluster OpenShift** : Force de `-450` (forme une orbite stable autour de son Hub).
- **Nœud Worker** : Force de `-120`.
- **Namespace FinOps** : Force de `-60` (forme des grappes orbitales resserrées autour des clusters).

### B. Liaisons Élastiques (`d3.forceLink`)
Les liens physiques entre couches possèdent une raideur et une longueur au repos différentiées :
- `HUB_TO_CLUSTER` : Distance au repos = **180 px**, épaisseur = 2 px.
- `CLUSTER_TO_NODE` : Distance au repos = **80 px**, épaisseur = 1.2 px.
- `NODE_TO_NAMESPACE` : Distance au repos = **45 px**, tracé en pointillés fins (`stroke-dasharray: 3,3`).

### C. Évitement de Collisions (`d3.forceCollide`)
Chaque nœud dispose d'une zone tampon d'exclusion stricte proportionnelle à son rayon, garantissant qu'aucun label ni cercle ne se chevauche lors des animations.

---

## 4. Interactions Utilisateur & Fonctionnalités Clés

### 1. Navigation Canvas Fluide (Zoom & Pan)
- **Molette / Tactile** : Zoom continu de `0.15x` à `4x` avec amorti cinétique.
- **Glisser le fond** : Déplacement panoramique (Pan) avec curseur dynamique (`cursor-grab` $\rightarrow$ `cursor-grabbing`).
- **Barre d'outils dédiée** : Boutons *Zoom Avant (+)*, *Zoom Arrière (-)*, *Recentrer (Home)* et *Réorganiser la Force (Reheat)*.

### 2. Épinglage Physique des Nœuds (Drag & Pin)
- L'utilisateur peut saisir n'importe quel nœud avec la souris et le déplacer.
- À la libération, le nœud fige ses coordonnées (`fx`, `fy`), permettant d'organiser manuellement une vue de présentation personnalisée.
- Un bouton dans le panneau d'inspection permet de déverrouiller la position physique à tout moment.

### 3. Filtres Multi-Critères en Temps Réel
- **Filtrage par niveau** : Boutons bascules pour afficher/masquer sélectivement les *Hubs*, *Clusters*, *Nœuds*, ou *Namespaces*.
- **Filtrage par environnement** : Sélecteur instantané (`Production`, `Staging`, `Development`, `Labs`).
- **Recherche textuelle floue** : Met en surbrillance avec effet néon les éléments correspondants et atténue les nœuds non pertinents à 15% d'opacité.

### 4. Tiroir d'Inspection Contextuel (Inspector Drawer)
Un clic sur un nœud ouvre instantanément un panneau latéral en verre dépoli (Frosted Glass UI) affichant :
- Type de ressource, statut d'activité et environnement.
- **Si Namespace** : Grade FinOps (A à F), pourcentage d'efficience, coût mensuel et bouton d'action vers le simulateur FinOps.
- **Si Cluster** : Version OpenShift, région cloud, nombre de nœuds et lien direct vers l'inventaire détaillé du cluster.
- **Si Nœud** : Rôle Kubernetes, capacité matérielle CPU/RAM et identifiant parent.

---

## 5. Spécification des APIs Backend

### Endpoint Principal :
```http
GET /api/v1/infrastructure/topology/graph
```

#### Exemple de Réponse JSON :
```json
{
  "summary": {
    "totalHubs": 2,
    "totalClusters": 5,
    "totalNodes": 64,
    "totalNamespaces": 30,
    "totalPhysicalHosts": 0,
    "totalCores": 1256.0,
    "totalMemoryGb": 5024.0
  },
  "nodes": [
    {
      "id": "hub-9c49c249",
      "label": "acm-hub-primary-eu",
      "type": "HUB",
      "status": "ACTIVE",
      "metadata": { "clusterCount": 3, "apiUrl": "https://api.acm-hub-primary.internal:6443" }
    },
    {
      "id": "cluster-ocp-prod-eu-west-01",
      "label": "ocp-prod-eu-west-01",
      "type": "CLUSTER",
      "parentId": "hub-9c49c249",
      "environment": "PRODUCTION",
      "metadata": { "region": "AWS eu-west-1", "nodeCount": 18 }
    },
    {
      "id": "ns-payments-api",
      "label": "payments-api",
      "type": "NAMESPACE",
      "parentId": "cluster-ocp-prod-eu-west-01",
      "efficiencyPercent": 42.5,
      "rating": "D",
      "monthlyCost": 1240.0,
      "team": "Payment Gateway Core"
    }
  ],
  "links": [
    {
      "source": "hub-9c49c249",
      "target": "cluster-ocp-prod-eu-west-01",
      "type": "HUB_TO_CLUSTER",
      "value": 2.0
    },
    {
      "source": "cluster-ocp-prod-eu-west-01",
      "target": "ns-payments-api",
      "type": "CLUSTER_TO_NODE",
      "value": 1.0
    }
  ]
}
```

---

## 6. Guide des Tests & Vérifications

1. **Vérification API Backend** :
   ```bash
   curl -s http://localhost:8080/api/v1/infrastructure/topology/graph | jq .summary
   ```
2. **Compilation Frontend Angular 18** :
   ```bash
   cd frontend && NG_CLI_ANALYTICS=false npm run build
   ```
3. **Accès Utilisateur dans le Portail** :
   - Via la navigation latérale : `Infrastructure & Topo` (badge violet `D3.JS`).
   - Via l'onglet de vue : Bascule immédiate entre `Topologie D3` et `Matrice Matérielle`.
   - Via l'URL directe : `http://localhost:4200/infrastructure` ou `http://localhost:4200/topology`.
