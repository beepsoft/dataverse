# Build solr-updater

```
DOCKER_BUILDKIT=1 docker build -t dataverse-solr-updater:latest ./modules/dataverse-solr-updater
```


# Enable CORS with "Access-Control-Allow-Origin: "*":

```
curl -X PUT -d 'true' http://localhost:8080/api/admin/settings/:AllowCors
```

# Associate CEDAR user with Dataverse superuser (dataverseAdmin)
# Once associated users can authenticate using their CEDAR api key
# as well, practically allow one to log into CEDAR and export
# MDB-s to dataverse
```
curl -X POST http://localhost:8080/api/admin/cedar/setCedarKey \
-H 'Content-Type: application/json' \
-d '{
    "userIdentifier": "dataverseAdmin",
    "cedarKey": "<<CEDAR KEY>"
}'
```