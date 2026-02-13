# dataverse-solr-updater

Dataverse solr updater is supposed to be run next to the Solr instance of a Dataverse installation and is used to synchronize Dataverse's metadatablock data with their accompanied Solr indexes. For this to work the updater must be able to write the `schema.xml` file of the Solr collection.

Invoke it like this:

    http://localhost:8984

or

    http://localhost:8984?returnSchema

to get back the updated schema.xml. 

It works by essentially invoking the following scripts:

```
bash -c curl ${DATAVERSE_URL}/api/admin/index/solr/schema | ./update-fields.sh ${SOLR_SCHEMA_XML_PATH}`
```

Script depends on the following env vars:

- `DATAVERSE_URL`: base URL of the Dataverse installation as accessible by this script. Defaults to http://host.docker.internal:8080
- `SOLR_URL`: base URL of the solr installation as accessible by this script. Defaults to http://solr:8983
- `SOLR_SCHEMA_XML_PATH`: path of solr's schmea.xml as accessible by this script. Defaults to /var/solr/data/collection1/conf/schema.xml

The default values assume that it is run from docker together with Solr (and Postgresql):

```yaml
version: '3.7'
services:
  postgresql:
    image: "postgres:13"
    container_name: "dv-postgres"
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=secret
    ports:
      - '5432:5432'
    volumes:
      - ./postgresql:/var/lib/postgresql/data
  solr:
    image: "ghcr.io/gdcc/solr-k8s:5.9"
    container_name: "dv-solr"
    ports:
      - '8983:8983'
    volumes:
      - ./solr:/var/solr

  solr-updater:
    build: solr-updater
    container_name: "dv-solr-updater"
    environment:
      - DATAVERSE_URL=http://host.docker.internal:8080
      - SOLR_URL=http://solr:8983
      - SOLR_SCHEMA_XML_PATH=/var/solr/data/collection1/conf/schema.xml
    ports:
      - '8984:3000'
    volumes:
      - ./solr:/var/solr
```

## Development setup

Install dependencies:

```bash
bun install
```

Run:

```bash
bun run index.ts
```

