"use strict";

const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();

const FIXTURE = require('./fixtures/dynamo-record.json');
const PRESIGNED_URI = 'https://s3.example.com/presigned?token=xyz';
const FISCAL_CODE = 'RSSMRA80A01H501T';
const CORRELATION_ID = 'PF-12c09d1a-94de-4e0a-82e6-b7637ae49c62';
const FILE_KEY = 'safestorage-key-xyz';

function makeEvent(correlationId, fileKey, taxId) {
  return {
    pathParameters: { id: correlationId, url: fileKey },
    headers: {
      'x-pagopa-lollipop-user-id': taxId !== undefined ? taxId : FISCAL_CODE
    }
  };
}

function makeHandler(dynamo, safeStorage) {
  return proxyquire('../app/eventHandler', {
    './lib/dynamoDbClient': dynamo || { findByRequestIdAndRecipientTaxId: async () => FIXTURE },
    './lib/safeStorageClient': safeStorage || { getPresignedUri: async () => PRESIGNED_URI }
  });
}

describe('eventHandler', () => {
  beforeEach(() => {
    process.env.DOWNLOAD_MODE_REDIRECT = 'true';
  });

  describe('REDIRECT mode', () => {
    it('should return 302 with Location header when correlationId and taxId match', async () => {
      const handler = makeHandler();
      const result = await handler.handleEvent(makeEvent(CORRELATION_ID, FILE_KEY));
      expect(result.statusCode).to.equal(302);
      expect(result.headers.Location).to.equal(PRESIGNED_URI);
    });

  });

  describe('404 cases', () => {
    it('should return 404 when correlationId not found in DynamoDB', async () => {
      const handler = makeHandler({ findByRequestIdAndRecipientTaxId: async () => null });
      const result = await handler.handleEvent(makeEvent('non-existent', FILE_KEY));
      expect(result.statusCode).to.equal(404);
      expect(JSON.parse(result.body).status).to.equal(404);
    });
  });

  describe('403 cases', () => {
    it('should return 403 when x-pagopa-lollipop-user-id header is absent', async () => {
      const handler = makeHandler();
      const event = { pathParameters: { id: CORRELATION_ID, url: FILE_KEY }, headers: {} };
      const result = await handler.handleEvent(event);
      expect(result.statusCode).to.equal(403);
    });
  });

  describe('502 cases', () => {
    it('should return 502 when SafeStorage throws an error', async () => {
      const handler = makeHandler(
        null,
        { getPresignedUri: async () => { throw new Error('SafeStorage unavailable'); } }
      );
      const result = await handler.handleEvent(makeEvent(CORRELATION_ID, FILE_KEY));
      expect(result.statusCode).to.equal(502);
      expect(JSON.parse(result.body).status).to.equal(502);
    });
  });

  describe('400 cases', () => {
    it('should return 400 when path parameters are missing', async () => {
      const handler = makeHandler();
      const result = await handler.handleEvent({ pathParameters: {}, headers: {} });
      expect(result.statusCode).to.equal(400);
    });

    it('should return 400 when pathParameters is undefined', async () => {
      const handler = makeHandler();
      const result = await handler.handleEvent({ headers: {} });
      expect(result.statusCode).to.equal(400);
    });
  });

  describe('BYTESTREAM mode', () => {
    it('should return 200 with base64 body when DOWNLOAD_MODE_REDIRECT is false', async () => {
      process.env.DOWNLOAD_MODE_REDIRECT = 'false';
      const fakeBuffer = Buffer.from('%PDF-1.4 fake content');
      const handler = proxyquire('../app/eventHandler', {
        './lib/dynamoDbClient': { findByRequestIdAndRecipientTaxId: async () => FIXTURE },
        './lib/safeStorageClient': { getPresignedUri: async () => PRESIGNED_URI },
        'https': {
          get: (_url, callback) => {
            const mockRes = {
              statusCode: 200,
              headers: { 'content-type': 'application/pdf' },
              on: (event, handler) => {
                if (event === 'data') handler(fakeBuffer);
                if (event === 'end') handler();
                return mockRes;
              }
            };
            callback(mockRes);
            return { on: () => {} };
          }
        }
      });
      const result = await handler.handleEvent(makeEvent(CORRELATION_ID, FILE_KEY));
      expect(result.statusCode).to.equal(200);
      expect(result.isBase64Encoded).to.equal(true);
      expect(result.headers['Content-Type']).to.equal('application/octet-stream');
      expect(result.body).to.equal(fakeBuffer.toString('base64'));
    });
  });

  describe('DynamoDB error', () => {
    it('should return 500 when DynamoDB throws an error', async () => {
      const handler = makeHandler(
        { findByRequestIdAndRecipientTaxId: async () => { throw new Error('DynamoDB connection error'); } }
      );
      const result = await handler.handleEvent(makeEvent(CORRELATION_ID, FILE_KEY));
      expect(result.statusCode).to.equal(500);
    });
  });
});
