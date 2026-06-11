"use strict";

const dynamoDbClient = require('./lib/dynamoDbClient');
const safeStorageClient = require('./lib/safeStorageClient');
const responseBuilder = require('./lib/responseBuilder');
const https = require('https');
const http = require('http');

exports.handleEvent = async function (event) {
  console.log(JSON.stringify(event));

  const downloadModeRedirect = process.env.DOWNLOAD_MODE_REDIRECT === 'true';

  const pathParams = event.pathParameters || {};
  const requestId = pathParams.id;
  const fileKey = pathParams.url;

  if (!requestId || !fileKey) {
    return responseBuilder.error(400, 'Bad Request', 'Missing required path parameters: requestId / fileKey');
  }

  const recipientTaxId = event?.requestContext?.authorizer?.cx_id;

  console.log(`recipientTaxId in event: ${recipientTaxId}`);

  if (!recipientTaxId) {
    console.error('Missing \'x-pagopa-lollipop-user-id\' -> \'cx_id\' header!');
    return responseBuilder.error(403, 'Forbidden', 'Missing \'x-pagopa-lollipop-user-id\' -> \'cx_id\' header');
  }

  let entity;
  try {
    entity = await dynamoDbClient.findByRequestIdAndRecipientTaxId(requestId, recipientTaxId);
  } catch (err) {
    console.error('DynamoDB query error:', err);
    return responseBuilder.error(500, 'Internal Server Error', 'Error querying message store');
  }

  if (!entity) {
    return responseBuilder.error(404, 'Not Found', `Message with requestId ${requestId} not found`);
  }

  console.log(`pnIOConnectorRequest Entity found with requestId: ${requestId} and internalId: ${recipientTaxId}`);

  const checkAttachment = Array.isArray(entity.attachments) && entity.attachments.some(a => a.fileKey === fileKey);
  if (!checkAttachment) {
    return responseBuilder.error(404, 'Not Found', `File key ${fileKey} not found in request ${requestId} attachments`);
  }

  console.log(`pnIOConnectorRequest Entity attachments found with fileKey: ${fileKey}`);

  let presignedUri;
  try {
    presignedUri = await safeStorageClient.getPresignedUri(fileKey);
  } catch (err) {
    console.error('SafeStorage error:', err);
    return responseBuilder.error(502, 'Bad Gateway', 'Error obtaining presigned URI from SafeStorage');
  }

  console.log(`Obtained SafeStorage presignedUri: ${presignedUri}`);

  if (downloadModeRedirect) {
    console.log(`Returning 302 redirect to presignedUri`);
    return responseBuilder.redirect(presignedUri);
  } else {
    try {
      const buffer = await fetchBytes(presignedUri);
      console.log(`Obtained bytestream from presignedUri`);
      return responseBuilder.bytestream(buffer);
    } catch (err) {
      console.error('Bytestream fetch error:', err);
      return responseBuilder.error(502, 'Bad Gateway', 'Error fetching document content');
    }
  }
};

function fetchBytes(url) {
  const lib = url.startsWith('https') ? https : http;
  return new Promise((resolve, reject) => {
    lib.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        fetchBytes(res.headers.location).then(resolve).catch(reject);
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error(`Error invoking SafeStorage presignedUri ${res.statusCode}`));
        return;
      }
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}
