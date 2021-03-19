package org.edgegallery.mecm.apm.service;

import org.edgegallery.mecm.apm.model.dto.SyncBaseDto;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public interface RestService {
    /**
     * Synchronizes updated or inserted records.
     *
     * @param url           url of MEPM component
     * @param responseClass class to which response needs to be mapped
     * @param token         access token
     * @param <T>           type of body
     * @return response entity with body of type T
     */
    <T extends SyncBaseDto> ResponseEntity<T> syncRecords(String url, Class<T> responseClass, String token);

    /**
     * Send requests to desired end point.
     *
     * @param uri uri of end point
     * @param method http method
     * @param token access token
     * @param data body
     * @return response entity
     */
    ResponseEntity<String> sendRequest(String uri, HttpMethod method, String token, String data);
}
