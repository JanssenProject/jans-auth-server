/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.server.service;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jans.as.common.util.RedirectUri;
import io.jans.as.model.error.ErrorResponseFactory;
import io.jans.as.model.error.IErrorType;
import io.jans.as.server.util.RedirectUtil;

/**
 * @author Yuriy Zabrovarnyy
 */
public class RedirectUriResponse {

    private final static Logger log = LoggerFactory.getLogger(RedirectUriResponse.class);

    private RedirectUri redirectUri;
    private String state;
    private HttpServletRequest httpRequest;
    private ErrorResponseFactory errorFactory;
    private boolean fapiCompatible = false;

    public RedirectUriResponse(RedirectUri redirectUri, String state, HttpServletRequest httpRequest, ErrorResponseFactory errorFactory) {
        this.redirectUri = redirectUri;
        this.state = state;
        this.httpRequest = httpRequest;
        this.errorFactory = errorFactory;
    }

    public WebApplicationException createWebException(IErrorType errorType) {
        return createWebException(errorType, null);
    }

    public WebApplicationException createWebException(IErrorType errorType, String reason) {
        if (fapiCompatible) {
            log.trace("Reason: " + reason); // print reason and set it to null since FAPI does not allow unknown fields in response
            reason = null;
        }
        redirectUri.parseQueryString(errorFactory.getErrorAsQueryString(errorType, state, reason));
        return new WebApplicationException(RedirectUtil.getRedirectResponseBuilder(redirectUri, httpRequest).build());
    }

    public void setState(String state) {
        this.state = state;
    }

    public Response.ResponseBuilder createErrorBuilder(IErrorType errorType) {
        redirectUri.parseQueryString(errorFactory.getErrorAsQueryString(errorType, state));
        return RedirectUtil.getRedirectResponseBuilder(redirectUri, httpRequest);
    }

    public RedirectUri getRedirectUri() {
        return redirectUri;
    }

    public boolean isFapiCompatible() {
        return fapiCompatible;
    }

    public void setFapiCompatible(boolean fapiCompatible) {
        this.fapiCompatible = fapiCompatible;
    }
}
