package io.jans.as.server.par.ws.rs;

import com.google.common.collect.Lists;
import io.jans.as.common.model.registration.Client;
import io.jans.as.model.authorize.AuthorizeErrorResponseType;
import io.jans.as.model.common.ResponseType;
import io.jans.as.model.configuration.AppConfiguration;
import io.jans.as.model.crypto.AbstractCryptoProvider;
import io.jans.as.model.error.ErrorResponseFactory;
import io.jans.as.model.jwt.JwtClaimName;
import io.jans.as.persistence.model.Par;
import io.jans.as.server.authorize.ws.rs.AuthorizeRestWebServiceValidator;
import io.jans.as.server.model.authorize.Claim;
import io.jans.as.server.model.authorize.IdTokenMember;
import io.jans.as.server.model.authorize.JwtAuthorizationRequest;
import io.jans.as.server.model.authorize.ScopeChecker;
import io.jans.as.server.service.RedirectUriResponse;
import io.jans.as.server.service.RequestParameterService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

/**
 * @author Yuriy Zabrovarnyy
 */
@ApplicationScoped
public class ParValidator {

    @Inject
    private Logger log;

    @Inject
    private AbstractCryptoProvider cryptoProvider;

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private AuthorizeRestWebServiceValidator authorizeRestWebServiceValidator;

    @Inject
    private ScopeChecker scopeChecker;

    @Inject
    private ErrorResponseFactory errorResponseFactory;

    @Inject
    private RequestParameterService requestParameterService;

    public void validateRequestUriIsAbsent(RedirectUriResponse redirectUriResponse, String requestUri) {
        if (StringUtils.isBlank(requestUri))
            return;

        log.trace("request_uri parameter is not allowed at PAR endpoint. Return error.");
        throw redirectUriResponse.createWebException(AuthorizeErrorResponseType.INVALID_REQUEST);
    }

    public void validateRequestObject(RedirectUriResponse redirectUriResponse, Par par, Client client) {
        final String request = par.getAttributes().getRequest();

        if (StringUtils.isBlank(request)) {
            return;
        }

        List<ResponseType> responseTypes = ResponseType.fromString(par.getAttributes().getResponseType(), " ");

        try {
            JwtAuthorizationRequest jwtRequest = JwtAuthorizationRequest.createJwtRequest(request, null, client, redirectUriResponse, cryptoProvider, appConfiguration);

            if (jwtRequest == null) {
                throw authorizeRestWebServiceValidator.createInvalidJwtRequestException(redirectUriResponse, "Failed to parse jwt.");
            }
            if (StringUtils.isNotBlank(jwtRequest.getState())) {
                par.getAttributes().setState(jwtRequest.getState());
                redirectUriResponse.setState(jwtRequest.getState());
            }
            if (appConfiguration.getFapiCompatibility() && StringUtils.isBlank(jwtRequest.getState())) {
                par.getAttributes().setState(""); // #1250 - FAPI : discard state if in JWT we don't have state
                redirectUriResponse.setState("");
            }

            authorizeRestWebServiceValidator.validateRequestObject(jwtRequest, redirectUriResponse);

            // MUST be equal
            if (!jwtRequest.getResponseTypes().containsAll(responseTypes) || !responseTypes.containsAll(jwtRequest.getResponseTypes())) {
                throw authorizeRestWebServiceValidator.createInvalidJwtRequestException(redirectUriResponse, "The responseType parameter is not the same in the JWT");
            }
            if (StringUtils.isBlank(jwtRequest.getClientId()) || !jwtRequest.getClientId().equals(par.getAttributes().getClientId())) {
                throw authorizeRestWebServiceValidator.createInvalidJwtRequestException(redirectUriResponse, "The clientId parameter is not the same in the JWT");
            }

            Set<String> scopes = scopeChecker.checkScopesPolicy(client, par.getAttributes().getScope());
            // JWT wins
            if (!jwtRequest.getScopes().isEmpty()) {
                if (!scopes.contains("openid")) { // spec: Even if a scope parameter is present in the Request Object value, a scope parameter MUST always be passed using the OAuth 2.0 request syntax containing the openid scope value
                    throw new WebApplicationException(Response
                            .status(Response.Status.BAD_REQUEST)
                            .entity(errorResponseFactory.getErrorAsJson(io.jans.as.model.authorize.AuthorizeErrorResponseType.INVALID_SCOPE, par.getAttributes().getState(), "scope parameter does not contain openid value which is required."))
                            .build());
                }
                scopes = scopeChecker.checkScopesPolicy(client, Lists.newArrayList(jwtRequest.getScopes()));
                par.getAttributes().setScope(io.jans.as.model.util.StringUtils.implode(scopes, " "));
            }
            if (jwtRequest.getRedirectUri() != null && !jwtRequest.getRedirectUri().equals(par.getAttributes().getRedirectUri())) {
                throw authorizeRestWebServiceValidator.createInvalidJwtRequestException(redirectUriResponse, "The redirect_uri parameter is not the same in the JWT");
            }
            if (StringUtils.isNotBlank(jwtRequest.getNonce())) {
                par.getAttributes().setNonce(jwtRequest.getNonce());
            }
            if (StringUtils.isNotBlank(jwtRequest.getCodeChallenge())) {
                par.getAttributes().setCodeChallenge(jwtRequest.getCodeChallenge());
            }
            if (StringUtils.isNotBlank(jwtRequest.getCodeChallengeMethod())) {
                par.getAttributes().setCodeChallengeMethod(jwtRequest.getCodeChallengeMethod());
            }
            if (jwtRequest.getDisplay() != null && StringUtils.isNotBlank(jwtRequest.getDisplay().getParamName())) {
                par.getAttributes().setDisplay(jwtRequest.getDisplay().getParamName());
            }
            if (!jwtRequest.getPrompts().isEmpty()) {
                par.getAttributes().setPrompt(jwtRequest.getJsonPayload().optString("prompt"));
            }
            if (jwtRequest.getResponseMode() != null) {
                redirectUriResponse.getRedirectUri().setResponseMode(jwtRequest.getResponseMode());
                par.getAttributes().setResponseMode(jwtRequest.getJsonPayload().optString("response_mode"));
            }

            final IdTokenMember idTokenMember = jwtRequest.getIdTokenMember();
            if (idTokenMember != null) {
                if (idTokenMember.getMaxAge() != null) {
                    par.getAttributes().setMaxAge(idTokenMember.getMaxAge());
                }
                final Claim acrClaim = idTokenMember.getClaim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE);
                if (acrClaim != null && acrClaim.getClaimValue() != null) {
                    par.getAttributes().setAcrValuesStr(acrClaim.getClaimValue().getValueAsString());
                }
            }
            requestParameterService.getCustomParameters(jwtRequest, par.getAttributes().getCustomParameters());
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Invalid JWT authorization request. Message : " + e.getMessage(), e);
            throw authorizeRestWebServiceValidator.createInvalidJwtRequestException(redirectUriResponse, "Invalid JWT authorization request");
        }
    }
}
