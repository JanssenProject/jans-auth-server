/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.common.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import io.jans.as.model.common.ResponseMode;
import io.jans.as.model.common.ResponseType;
import io.jans.as.model.util.Util;

/**
 * @author Javier Rojas Blum
 * @version October 7, 2019
 */
public class RedirectUri {

    private String baseRedirectUri;
    private List<ResponseType> responseTypes;
    private ResponseMode responseMode;
    private Map<String, String> responseParameters;

    public RedirectUri(String baseRedirectUri) {
        this.baseRedirectUri = baseRedirectUri;
        this.responseMode = ResponseMode.QUERY;

        responseParameters = new HashMap<String, String>();
    }

    public RedirectUri(String baseRedirectUri, List<ResponseType> responseTypes, ResponseMode responseMode) {
        this(baseRedirectUri);
        this.responseTypes = responseTypes;
        this.responseMode = responseMode;
    }

    public String getBaseRedirectUri() {
        return baseRedirectUri;
    }

    public void setBaseRedirectUri(String baseRedirectUri) {
        this.baseRedirectUri = baseRedirectUri;
    }

    public ResponseMode getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(ResponseMode responseMode) {
        this.responseMode = responseMode;
    }

    public void addResponseParameter(String key, String value) {
        if (StringUtils.isNotBlank(key)) {
            responseParameters.put(key, value);
        }
    }

    public void parseQueryString(String queryString) {
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(queryString, "&", false);
            while (st.hasMoreElements()) {
                String nameValueToken = st.nextElement().toString();

                StringTokenizer stParamValue = new StringTokenizer(nameValueToken, "=", false);

                if (stParamValue.countTokens() == 1) {
                    String paramName = stParamValue.nextElement().toString();
                    responseParameters.put(paramName, null);
                } else if (stParamValue.countTokens() == 2) {
                    try {
                        String paramName = stParamValue.nextElement().toString();
                        String paramValue = URLDecoder.decode(stParamValue.nextElement().toString(), Util.UTF8_STRING_ENCODING);
                        responseParameters.put(paramName, paramValue);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public String getQueryString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : responseParameters.entrySet()) {
            try {
                if (StringUtils.isNotBlank(entry.getKey()) && StringUtils.isNotBlank(entry.getValue())) {
                    if (sb.length() > 0) {
                        sb.append('&');
                    }
                    sb.append(URLEncoder.encode(entry.getKey(), Util.UTF8_STRING_ENCODING));
                    sb.append('=').append(URLEncoder.encode(entry.getValue(), Util.UTF8_STRING_ENCODING));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    private void appendQuerySymbol(StringBuilder sb) {
        if (!sb.toString().contains("?")) {
            sb.append("?");
        } else {
            sb.append("&");
        }
    }

    private void appendFragmentSymbol(StringBuilder sb) {
        if (!sb.toString().contains("#")) {
            sb.append("#");
        } else {
            sb.append("&");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(baseRedirectUri);

        if (responseParameters.size() > 0) {
            if (responseMode != ResponseMode.FORM_POST) {
                if (responseMode != null) {
                    if (responseMode == ResponseMode.QUERY) {
                        appendQuerySymbol(sb);
                    } else if (responseMode == ResponseMode.FRAGMENT) {
                        appendFragmentSymbol(sb);
                    }
                } else if (responseTypes != null && (responseTypes.contains(ResponseType.TOKEN) || responseTypes.contains(ResponseType.ID_TOKEN))) {
                    appendFragmentSymbol(sb);
                } else {
                    appendQuerySymbol(sb);
                }
                sb.append(getQueryString());
            }

            if (responseMode == ResponseMode.FORM_POST) {
                sb = new StringBuilder();
                sb.append("<html>");
                sb.append("<head><title>oxAuth - Submit This Form</title></head>");
                sb.append("<body onload=\"javascript:document.forms[0].submit()\">");
                //sb.append("<body>");
                sb.append("<form method=\"post\" action=\"").append(baseRedirectUri).append("\">");
                for (Map.Entry<String, String> entry : responseParameters.entrySet()) {
                    String entryValue = StringEscapeUtils.escapeHtml(entry.getValue());
                    sb.append("<input type=\"hidden\" name=\"").append(entry.getKey()).append("\" value=\"").append(entryValue).append("\"/>");
                }
                sb.append("</form>");
                sb.append("</body>");
                sb.append("</html>");
            }
        }
        return sb.toString();
    }
}