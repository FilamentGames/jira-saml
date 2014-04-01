/*
 * SAMLAuthenticator - authenticate JIRA users using SAML
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Copyright (c) 2014 LastPass, Inc.
 */
package com.lastpass.jira;

import com.lastpass.saml.SAMLInit;
import com.lastpass.saml.SAMLClient;
import com.lastpass.saml.SAMLException;
import com.lastpass.saml.SAMLUtils;
import com.lastpass.saml.IdPConfig;
import com.lastpass.saml.SPConfig;
import com.lastpass.saml.AttributeSet;

import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.UnsupportedEncodingException;

import com.atlassian.jira.security.login.JiraSeraphAuthenticator;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SAMLAuthenticator extends JiraSeraphAuthenticator
{
    private static final long serialVersionUID = 7194588257005733504L;

    private SAMLClient client;
    private static final Logger logger = LoggerFactory
        .getLogger(SAMLAuthenticator.class);

    public SAMLAuthenticator()
        throws SAMLException
    {
        SAMLInit.initialize();

        IdPConfig idpConfig = new IdPConfig(new File("idp-metadata.xml"));
        SPConfig spConfig = new SPConfig(new File("sp-metadata.xml"));
        client = new SAMLClient(spConfig, idpConfig);
    }

    public String getRedirectUrl(String relayState)
    {
        String requestId = SAMLUtils.generateRequestId();
        try {
            String authrequest = client.generateAuthnRequest(requestId);
            String url = client.getIdPConfig().getLoginUrl();
            url = url +
                "?SAMLRequest=" + URLEncoder.encode(authrequest, "UTF-8");

            if (relayState != null)
                url += "&RelayState=" + URLEncoder.encode(relayState, "UTF-8");

            return url;
        } catch (SAMLException e) {
            logger.error("Could not generate AuthnRequest", e);
        } catch (UnsupportedEncodingException e) {
            logger.error("Missing UTF-8 support", e);
        }
        return null;
    }

    @Override
    public Principal getUser(HttpServletRequest request,
                             HttpServletResponse response)
    {
        // already authed in this session?
        Principal sessionPrincipal = getUserFromSession(request);
        if (sessionPrincipal != null)
            return sessionPrincipal;

        if (request.getParameter("SAMLResponse") == null) {
            // we don't have a user, nor a saml token to look at.
            // return null, so caller will redirect to saml login
            // page.
            return null;
        }

        // Consume and validate the assertions in the response.
        String authresponse = request.getParameter("SAMLResponse");
        AttributeSet aset;
        try {
            aset = client.validateResponse(authresponse);
        } catch (SAMLException e) {
            // response invalid.
            logger.error("SAML response invalid", e);
            return null;
        }

        String username = aset.getNameId();
        logger.debug("SAML user: " + username);

        Principal p = getUser(username);
        putPrincipalInSessionContext(request, p);
        return p;
    }
}