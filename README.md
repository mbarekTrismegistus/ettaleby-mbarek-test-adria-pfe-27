# Plateforme bancaire — ADRIA PFE 2027

Projet Maven multi-modules en Java 21. Les services métier exposent les mêmes opérations via REST, GraphQL et MCP et utilisent chacun une base H2 en mémoire.

## Partie D — Services techniques

| Service | Port | Rôle |
| --- | --- | --- |
| discovery-service | 8761 | Registre Eureka |
| config-service | 8888 | Configuration centralisée, backend natif `config-repo/` |
| gateway-service | 8080 | Point d'entrée HTTP, découverte et répartition des appels |
| account-service | 8081 | Comptes et soldes |
| transaction-service | 8082 | Paiements et virements, appels OpenFeign vers les comptes |

La configuration commune fournit l'adresse Eureka et les réglages de découverte. `config-repo/transaction-service.properties` contient `bank.transaction.max-amount=1000`. La gateway utilise des destinations `lb://` résolues par Eureka. Ses circuits Resilience4J retournent une réponse HTTP 503 au format Problem Detail lorsqu'un service est indisponible.

### Compilation et démarrage

Prérequis : JDK 21 et Maven. Depuis la racine :

```sh
mvn -pl config-service,discovery-service,gateway-service,account-service,transaction-service verify
```

Démarrer chaque commande dans un terminal distinct, dans cet ordre, depuis la racine du dépôt. Attendre que le service précédent soit démarré :

```sh
java -jar discovery-service/target/discovery-service-0.0.1-SNAPSHOT.jar
java -jar config-service/target/config-service-0.0.1-SNAPSHOT.jar
java -jar account-service/target/account-service-0.0.1-SNAPSHOT.jar
java -jar transaction-service/target/transaction-service-0.0.1-SNAPSHOT.jar
java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

Eureka peut prendre quelques secondes pour propager les inscriptions. Son tableau de bord est disponible sur <http://localhost:8761>. Les variables suivantes permettent d'adapter les adresses :

| Variable | Valeur par défaut | Utilisation |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Clients Config Server |
| `EUREKA_URL` | `http://localhost:8761/eureka` | Registre des services |
| `CONFIG_REPO_PATH` | `file:./config-repo,file:../config-repo` | Emplacements du backend natif, par exemple `file:/chemin/absolu/config-repo/` |

### Routes de la gateway

| URL via le port 8080 | Destination |
| --- | --- |
| `/api/accounts/**` | account-service, chemin conservé |
| `/api/transactions/**` | transaction-service, chemin conservé |
| `/account-service/graphql` | account-service `/graphql` |
| `/transaction-service/graphql` | transaction-service `/graphql` |
| `/account-service/mcp` | account-service `/mcp` |
| `/transaction-service/mcp` | transaction-service `/mcp` |
| `/api/agent/**` | Route préparée pour la partie E ; retourne 503 sans instance de l'agent |

Le préfixe `/<service>/` est retiré avant transmission. Le navigateur Angular sur `http://localhost:4200` est autorisé par CORS, avec transmission des en-têtes MCP et exposition de `Mcp-Session-Id`.

### Vérifications manuelles

```sh
# Configuration réellement servie
curl http://localhost:8888/transaction-service/default

# Santé et routes de la gateway
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/gateway/routes

# API REST via découverte Eureka
curl http://localhost:8080/api/accounts
curl http://localhost:8080/api/transactions/limits

# GraphQL via la gateway
curl http://localhost:8080/transaction-service/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ maxTransactionAmount }"}'
```

Après modification du plafond dans `config-repo/transaction-service.properties`, recharger les propriétés du service sans redémarrage :

```sh
curl -X POST http://localhost:8082/actuator/refresh
curl http://localhost:8080/api/transactions/limits
```

### Vérification automatique complète

Après la compilation, avec les ports ci-dessus libres :

```sh
python3 scripts/verify-part-d.py
```

Si des services tournent déjà sur ces ports, utiliser `python3 scripts/verify-part-d.py --port-offset 10000` pour lancer une pile isolée sur les ports 18080–18082, 18761 et 18888.

Le script démarre les cinq services, vérifie les inscriptions Eureka, la configuration, les routes REST/GraphQL/MCP, un paiement, un virement avec contrôle des soldes, le rechargement du plafond et la réponse 503 après arrêt du service de comptes. Il utilise une copie temporaire de `config-repo/`, conserve les logs dans le dossier temporaire affiché et arrête les processus qu'il a démarrés. Les fichiers de configuration du dépôt ne sont pas modifiés.

Les tests Maven vérifient également la configuration native, les routes de la gateway, les corps et en-têtes MCP, CORS, la conservation des erreurs métier et le fallback 503.

### Périmètre actuel

Les réglages Eureka rapides et les endpoints Actuator exposés servent aux tests locaux. L'authentification Keycloak, Kafka, le frontend, l'analyse de fraude et les livrables DevOps restent à traiter dans les étapes suivantes. Les données H2 sont perdues à l'arrêt des services.

## Partie E — Assistant bancaire agentique

`ai-agent-service` écoute sur le port **8083** et expose son API via la gateway sur le port **8080**. Spring AI transmet au modèle les outils découverts sur les deux serveurs MCP, exécute les appels demandés par le modèle puis lui renvoie les résultats pour produire la réponse finale. Les contrôles métier restent dans les services bancaires.

L'agent dispose de 12 outils : consultation et gestion des comptes, paiements, virements, historique et plafond courant. Les outils `debitAccount` et `creditAccount` sont filtrés : les paiements et virements doivent passer par transaction-service pour être enregistrés. Le prompt demande de rechercher les données réelles, de préciser les informations ambiguës et de ne rapporter un succès qu'après confirmation par un outil.

### Configuration et démarrage

```sh
mvn -pl ai-agent-service verify
export OPENAI_API_KEY='votre-cle-api'
# Facultatif : le modèle par défaut est gpt-4.1-mini
export OPENAI_MODEL='gpt-4.1-mini'
java -jar ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar
```

Démarrer d'abord les cinq services de la partie D. Les serveurs MCP doivent être disponibles lors de l'initialisation de l'agent. La clé est lue depuis l'environnement, sans valeur factice par défaut ni stockage dans Git.

| Variable | Valeur par défaut | Rôle |
| --- | --- | --- |
| `OPENAI_API_KEY` | obligatoire | Clé du fournisseur LLM |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Modèle avec appels de fonctions |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | URL complète de base d'une API compatible Chat Completions, incluant `/v1` |
| `ACCOUNT_MCP_URL` | `http://localhost:8081` | Base du serveur MCP des comptes, sans `/mcp` |
| `TRANSACTION_MCP_URL` | `http://localhost:8082` | Base du serveur MCP des transactions, sans `/mcp` |

Les conversations conservent les 20 derniers messages en mémoire. Un redémarrage efface cet historique. Réutiliser le `conversationId` retourné pour poursuivre la même conversation ; l'omettre crée une nouvelle conversation. Les requêtes concurrentes d'une même conversation sont sérialisées.

### Tests avec un client REST

```sh
# Vérifier les outils disponibles sans appeler le LLM
curl http://localhost:8080/api/agent/tools

# Démarrer une conversation
curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Liste les comptes du client CUST-001 avec leurs soldes."}'

# Continuer avec le conversationId reçu
curl http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"UUID-RETOURNE","message":"Quel est le plafond actuel pour un virement ?"}'

# Effacer l'historique
curl -X DELETE http://localhost:8080/api/agent/conversations/UUID-RETOURNE
```

Réponse du chat : `{"conversationId":"...","answer":"..."}`. Les messages vides, supérieurs à 4000 caractères et les UUID invalides donnent un HTTP 400. Une panne du modèle ou d'un outil donne un HTTP 503 sans exposer les détails du fournisseur. Les appels HTTP au modèle ne sont pas réessayés automatiquement ; une requête peut effectuer au maximum 12 appels d'outils. Après un échec incertain, consulter l'historique bancaire avant de réémettre une opération.

Le fichier `requests/agent.http` fournit également des exemples utilisables dans un client REST. Le client REST satisfait le choix demandé par la partie E ; Telegram et Discord ne sont pas nécessaires pour ce parcours.

### Vérification reproductible

```sh
# Compiler les six modules concernés, puis tester sans clé ni accès Internet
mvn -pl config-service,discovery-service,gateway-service,account-service,transaction-service,ai-agent-service verify
python3 scripts/verify-part-e.py

# Variante avec le fournisseur réel et des instructions en français
python3 scripts/verify-part-e.py --live-llm
```

Le script utilise par défaut les ports habituels + 10000 et une copie temporaire de la configuration ; `--port-offset` permet de changer ce décalage. Il démarre et arrête sa propre pile. En mode déterministe, seul le fournisseur LLM est simulé : les appels HTTP au modèle, les deux clients MCP, les services métier, Eureka et la gateway sont réels. Le script vérifie la découverte des outils, un paiement, un virement, les trois écritures comptables correspondantes, les soldes 650/100, la mémoire, sa suppression et une panne du fournisseur sans nouvelle tentative. `--live-llm` utilise les variables LLM ci-dessus et effectue les opérations uniquement dans les bases H2 temporaires de la pile de test ; il nécessite l'accès réseau et peut consommer des crédits API.

Les conversations sont identifiées par un UUID, sans contrôle de propriétaire tant que Keycloak n'est pas intégré. L'agent reste destiné à la démonstration locale à ce stade.

Références : [Spring AI MCP Client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html), [appels de fonctions OpenAI](https://developers.openai.com/api/docs/guides/function-calling), [modèle GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini).
