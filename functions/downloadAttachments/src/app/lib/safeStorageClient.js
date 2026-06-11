"use strict";

const { URL } = require('url');

const BASE_URL = process.env.PN_SAFESTORAGE_BASE_URL;
const GET_FILE_PATH = process.env.PN_SAFESTORAGE_GET_FILE_PATH;
const SAFESTORAGE_CX_ID = process.env.PN_SAFESTORAGE_CX_ID;
const PRESIGNED_URL_FIELD = process.env.PN_SAFESTORAGE_PRESIGNED_URL || 'download.url';

const { protocol, hostname, port } = new URL(BASE_URL);
const transport = protocol === 'https:' ? require('https') : require('http');

function extractField(obj, fieldPath) {
  return fieldPath.split('.').reduce((acc, key) => acc && acc[key], obj);
}

async function getPresignedUri(fileKey) {
  console.log(`Requesting presigned URI from SafeStorage for fileKey: ${fileKey}`);
  const options = {
    method: 'GET',
    hostname,
    port: port || (protocol === 'https:' ? 443 : 80),
    path: GET_FILE_PATH + '/' + fileKey,
    headers: {
      'x-pagopa-safestorage-cx-id': SAFESTORAGE_CX_ID,
      'Content-Type': 'application/json',
      ...(process.env._X_AMZN_TRACE_ID && { 'X-Amzn-Trace-Id': process.env._X_AMZN_TRACE_ID })
    }
  };
  return new Promise((resolve, reject) => {
    const req = transport.request(options, (res) => {
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
