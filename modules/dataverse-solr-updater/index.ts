// DATAVERSE_URL=http://localhost:8080 SOLR_URL=http://localhost:8983 SOLR_SCHEMA_XML_PATH=../solr/data/collection1/conf/schema.xml bun run index.ts
/**
 * Update solr index with new MDB field values.
 *
 * Call it like:
 *
 *    http://localhost:8984
 *
 *      or
 *
 *    http://localhost:8984?returnSchema
 *
 * to get back the update schema.xml
 *
 * Script depends on the following env vars:
 *
 *  - DATAVERSE_URL: base URL of the Dataverse installation as accessible by this script
 *  - SOLR_URL: base URL of the solr installation as accessible by this script
 *  - SOLR_SCHEMA_XML_PATH: path of solr's schmea.xml as accessible by this script
 *
 *
 * */
import {spawn} from "bun";

console.log(`Solr updater started. (bun version: ${Bun.version})`);

const DATAVERSE_URL = process.env["DATAVERSE_URL"] || "http://host.docker.internal:8080"
const SOLR_URL = process.env["SOLR_URL"] || "http://solr:8983"
const SOLR_SCHEMA_XML_PATH = process.env["SOLR_SCHEMA_XML_PATH"] || "/var/solr/data/collection1/conf/schema.xml"

export default {
  port: 3000,
  async fetch(req: Request) {
    // Ignore favicon.ico downloads
    if (req.url.endsWith("favicon.ico")) {
      return new Response(`Ignored.`);
    }

    // Update schema.xml
    // curl "http://localhost:8080/api/admin/index/solr/schema" | update-fields.sh /usr/local/solr/server/solr/collection1/conf/schema.xml
    console.log(`Executing bash -c curl ${DATAVERSE_URL}/api/admin/index/solr/schema | bash ./update-fields.sh ${SOLR_SCHEMA_XML_PATH}`)
    let spawnRes = spawn(["bash", "-c", `curl ${DATAVERSE_URL}/api/admin/index/solr/schema | bash ./update-fields.sh ${SOLR_SCHEMA_XML_PATH}`])
    await new Response(spawnRes.stdout).text()

    // Reload schema.xml
    //curl "http://localhost:8983/solr/admin/cores?action=RELOAD&core=collection1"
    console.log(`Invoking ${SOLR_URL+"/solr/admin/cores?action=RELOAD&core=collection1"}`)
    const dvResp = await fetch(SOLR_URL+"/solr/admin/cores?action=RELOAD&core=collection1");
    if (!dvResp.ok) {
      throw new Error(`HTTP error! Status: ${dvResp.status}`);
    }
    console.log(await dvResp.text())

    let url = new URL(req.url)
    if (url.searchParams.has("returnSchema")) {
      // Read in the updated scheam.xml to return to caller
      spawnRes = spawn(["cat", `${SOLR_SCHEMA_XML_PATH}`])
      const schemaXml = await new Response(spawnRes.stdout).text()
      console.log(schemaXml)

      if (req.headers.get("content-type") == "text/html") {
        return new Response(`Updated schema.xml:<br/> <pre>${schemaXml}</pre>`);
      }

      return new Response(`Updated schema.xml:\n ${schemaXml}`);
    }
    else {
      return new Response(`Ok.`);
    }
  }
}
