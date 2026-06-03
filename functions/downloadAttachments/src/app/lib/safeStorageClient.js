"use strict";

const http = require(process.env.PN_SAFESTORAGE_PROTOCOL);

const API_KEY = process.env.PN_SAFESTORAGE_API_KEY;
const HOSTNAME = process.env.PN_SAFESTORAGE_HOSTNAME;
const PORT = process.env.PN_SAFESTORAGE_PORT;
const GET_FILE_PATH = process.env.PN_SAFESTORAGE_GET_FILE_PATH;
const SAFESTORAGE_CX_ID = process.env.PN_SAFESTORAGE_CX_ID;
const PRESIGNED_URL_FIELD = process.env.PN_SAFESTORAGE_PRESIGNED_URL || 'download.url';

function extractField(obj, fieldPath) {
  return fieldPath.split('.').reduce((acc, key) => acc && acc[key], obj);
}

async function getPresignedUri(fileKey) {
  console.log(`Requesting presigned URI from SafeStorage for fileKey: ${fileKey}`);
  const options = {
    method: 'GET',
    hostname: HOSTNAME,
    port: PORT,
    path: GET_FILE_PATH + '/' + fileKey,
    headers: {
      'x-api-key': API_KEY,
      'x-pagopa-safestorage-cx-id': SAFESTORAGE_CX_ID,
      'Content-Type': 'application/json'
    }
  };
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode !== 200) {
          reject(new Error(`SafeStorage responded with status ${res.statusCode}: ${body}`));
          return;
        }
        let parsed;
        try {
          parsed = JSON.parse(body);
        } catch (e) {
          reject(new Error(`SafeStorage response is not valid JSON: ${body}`));
          return;
        }
        const uri = extractField(parsed, PRESIGNED_URL_FIELD);
        if (!uri) {
          reject(new Error(`Field '${PRESIGNED_URL_FIELD}' not found in SafeStorage response`));
          return;
        }
        console.log('Presigned URI obtained from SafeStorage');
        resolve(uri);
      });
    });
    req.on('error', reject);
    req.end();
  });
}

module.exports = { getPresignedUri };
