/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.connector.test.saml;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.common.model.idp.xsd.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.idp.xsd.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.xsd.AuthenticationStep;
import org.wso2.carbon.identity.application.common.model.xsd.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.xsd.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.xsd.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.xsd.LocalAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.xsd.Property;
import org.wso2.carbon.identity.application.common.model.xsd.ServiceProvider;
import org.wso2.carbon.identity.sso.saml.stub.types.SAMLSSOServiceProviderDTO;
import org.wso2.carbon.integration.common.utils.mgt.ServerConfigurationManager;
import org.wso2.carbon.um.ws.api.stub.ClaimValue;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.identity.connector.test.util.Utils;
import org.wso2.identity.integration.common.clients.Idp.IdentityProviderMgtServiceClient;
import org.wso2.identity.integration.common.clients.application.mgt.ApplicationManagementServiceClient;
import org.wso2.identity.integration.common.clients.sso.saml.SAMLSSOConfigServiceClient;
import org.wso2.identity.integration.common.clients.usermgt.remote.RemoteUserStoreManagerServiceClient;
import org.wso2.identity.integration.common.utils.ISIntegrationTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SMSOTPSecondFactorTestCase extends ISIntegrationTest {
    private static final Log log = LogFactory.getLog(SMSOTPSecondFactorTestCase.class);

    // SAML Application attributes
    private static final String USER_AGENT = "Apache-HttpClient/4.2.5 (java 1.5)";
    private static final String TRAVELOCITY_APP_NAME = "travelocity.com";
    private static final String ISSUER_NAME = TRAVELOCITY_APP_NAME;
    private static final String APPLICATION_NAME = "SAML-SSO-TestApplication";
    private static final String INBOUND_AUTH_TYPE = "samlsso";
    private static final String SMS_OTP = "SMSOTP";
    private static final String ATTRIBUTE_CS_INDEX_VALUE = "1239245949";
    private static final String ATTRIBUTE_CS_INDEX_NAME = "attrConsumServiceIndex";
    private static int TOMCAT_PORT = 8490;
    //private static int TOMCAT_PORT = 8080;

    // User Attributes
    private static final String USERNAME = "testUser";
    private static final String PASSWORD = "testUser";
    private static final String EMAIL = "testUser@wso2.com";
    private static final String NICKNAME = "testUserNick";
    private static final String MOBILE_NO = "+9473123456";

    private static final String ACS_URL = "http://localhost:" + TOMCAT_PORT + "/" + TRAVELOCITY_APP_NAME + "/home.jsp";
    private static final String COMMON_AUTH_URL = "https://localhost:9853/commonauth";
    private static final String SAML_SSO_LOGIN_URL =
            "http://localhost:" + TOMCAT_PORT + "/" + TRAVELOCITY_APP_NAME + "/samlsso?SAML2.HTTPBinding=HTTP-Redirect";

    //Claim Uris
    private static final String firstNameClaimURI = "http://wso2.org/claims/givenname";
    private static final String lastNameClaimURI = "http://wso2.org/claims/lastname";
    private static final String emailClaimURI = "http://wso2.org/claims/emailaddress";
    private static final String mobileClaimURI = "http://wso2.org/claims/mobile";

    private static final String profileName = "default";

    // Service Clients
    private ApplicationManagementServiceClient applicationManagementServiceClient;
    private RemoteUserStoreManagerServiceClient remoteUSMServiceClient;
    private SAMLSSOConfigServiceClient ssoConfigServiceClient;
    private DefaultHttpClient httpClient = new DefaultHttpClient();
    private Tomcat tomcatServer;
    private IdentityProviderMgtServiceClient identityProviderMgtServiceClient;
    private ServiceProvider serviceProvider;
    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {
        super.init();
        changeISConfiguration();
        super.init();
        // Initiating clients
        identityProviderMgtServiceClient = new IdentityProviderMgtServiceClient(sessionCookie, backendURL);
        ConfigurationContext configContext = ConfigurationContextFactory
                .createConfigurationContextFromFileSystem(null
                        , null);
        applicationManagementServiceClient =
                new ApplicationManagementServiceClient(sessionCookie, backendURL, configContext);
        remoteUSMServiceClient = new RemoteUserStoreManagerServiceClient(backendURL, sessionCookie);
        ssoConfigServiceClient =
                new SAMLSSOConfigServiceClient(backendURL, sessionCookie);

        createUser();
        createApplication();
        createIDP();

        updateSPWithSteps();
        //Starting tomcat
        log.info("Starting Tomcat");
        tomcatServer = getTomcat();
        URL resourceUrl = getClass().getResource(File.separator + "samples" + File.separator + "travelocity.com.war");
        startTomcat(tomcatServer, "/travelocity.com", resourceUrl.getPath());

    }



    @AfterClass(alwaysRun = true)
    public void testClear() throws Exception {
        deleteUser();
        deleteApplication();
        applicationManagementServiceClient = null;
        remoteUSMServiceClient = null;
        httpClient = null;
        //Stopping tomcat
        tomcatServer.stop();
        tomcatServer.destroy();
    }

    @Test(alwaysRun = true, description = "Testing SAML SSO login", groups = "wso2.is",
            priority = 1)
    public void testTOTPPage() {
        try {
            HttpResponse response;
            response = sendGetRequest(SAML_SSO_LOGIN_URL);
            String sessionKey = extractDataFromResponse(response, "name=\"sessionDataKey\"", 1);
            response = sendCredentials(sessionKey);
            Assert.assertTrue(response.getHeaders("LOCATION")[0].getValue().contains("https://localhost:9853/smsotpauthenticationendpoint/smsotp.jsp"));
            EntityUtils.consume(response.getEntity());
        } catch (Exception e) {
            Assert.fail("Did not redirect to sms authentication endpoint endpoint.", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing SAML SSO login", groups = "wso2.is",
            priority = 2)
    public void testTOTPAuthentication() {
        try {
            HttpResponse response;
            response = sendGetRequest(SAML_SSO_LOGIN_URL);
            String sessionKey = extractDataFromResponse(response, "name=\"sessionDataKey\"", 1);
            EntityUtils.consume(response.getEntity());
            response = sendCredentials(sessionKey);
            EntityUtils.consume(response.getEntity());
            String totp = getTOTPFromMessage("http://localhost:9855/getTesting");
            EntityUtils.consume(response.getEntity());
            response = sendOTP(sessionKey, totp, "false");
            EntityUtils.consume(response.getEntity());
            response = sendRedirectRequest(response);
            String samlResponse = Utils.extractDataFromResponse(response, "name='SAMLResponse'", 5);
            samlResponse = new String(Base64.decodeBase64(samlResponse));
            Assert.assertTrue(samlResponse.contains("<saml2:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\">testUser</saml2:NameID>"));

        } catch (Exception e) {
            Assert.fail("SAML SSO Login test failed.", e);
        }
    }

    private void startTomcat(Tomcat tomcat, String webAppUrl, String webAppPath)
            throws LifecycleException {
        tomcat.addWebapp(tomcat.getHost(), webAppUrl, webAppPath);
        tomcat.start();
    }

    private Tomcat getTomcat() {
        Tomcat tomcat = new Tomcat();
        tomcat.getService().setContainer(tomcat.getEngine());
        tomcat.setPort(TOMCAT_PORT);
        tomcat.setBaseDir(".");

        StandardHost stdHost = (StandardHost) tomcat.getHost();

        stdHost.setAppBase(".");
        stdHost.setAutoDeploy(true);
        stdHost.setDeployOnStartup(true);
        stdHost.setUnpackWARs(true);
        tomcat.setHost(stdHost);

        setSystemProperties();
        return tomcat;
    }

    private void setSystemProperties() {
        URL resourceUrl = getClass().getResource(File.separator + "keystores" + File.separator
                + "products" + File.separator + "wso2carbon.jks");
        System.setProperty("javax.net.ssl.trustStore", resourceUrl.getPath());
        System.setProperty("javax.net.ssl.trustStorePassword",
                "wso2carbon");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
    }

    private String extractDataFromResponse(HttpResponse response, String key, int token)
            throws IOException {
        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));
        String line;
        String value = "";

        while ((line = rd.readLine()) != null) {
            if (line.contains(key)) {
                String[] tokens = line.split("'");
                value = tokens[token];
            }
        }
        rd.close();
        return value;
    }

    private HttpResponse sendCredentials(String sessionKey) throws Exception {
        HttpPost post = new HttpPost(COMMON_AUTH_URL);
        post.setHeader("User-Agent", USER_AGENT);
        post.addHeader("Referer", ACS_URL);
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("username", USERNAME));
        urlParameters.add(new BasicNameValuePair("password", PASSWORD));
        urlParameters.add(new BasicNameValuePair("sessionDataKey", sessionKey));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        return httpClient.execute(post);
    }

    private HttpResponse sendOTP(String sessionKey, String totp, String resend) throws Exception {
        HttpPost post = new HttpPost(COMMON_AUTH_URL);
        post.setHeader("User-Agent", USER_AGENT);
        post.addHeader("Referer", ACS_URL);
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("resendCode", resend));
        urlParameters.add(new BasicNameValuePair("code", totp));
        urlParameters.add(new BasicNameValuePair("sessionDataKey", sessionKey));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        return httpClient.execute(post);
    }

    private String getTOTPFromMessage(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", USER_AGENT);
        HttpResponse response = httpClient.execute(request);
        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));
        String responseContent = rd.readLine();
        return responseContent.split("<response>Verification Code: ")[1].split("</response>")[0];
    }

    private HttpResponse sendGetRequest(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", USER_AGENT);
        return httpClient.execute(request);
    }

    private HttpResponse sendRedirectRequest(HttpResponse response) throws IOException {
        Header[] headers = response.getAllHeaders();
        String url = "";
        for (Header header : headers) {
            if ("Location".equals(header.getName())) {
                url = header.getValue();
            }
        }

        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", USER_AGENT);
        request.addHeader("Referer", ACS_URL);

        return httpClient.execute(request);
    }

    private void createApplication() throws Exception {

        serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationName(APPLICATION_NAME);
        serviceProvider.setDescription("This is a test Service Provider");
        applicationManagementServiceClient.createApplication(serviceProvider);

        serviceProvider = applicationManagementServiceClient.getApplication(APPLICATION_NAME);

        InboundAuthenticationRequestConfig requestConfig = new InboundAuthenticationRequestConfig();
        requestConfig.setInboundAuthType(INBOUND_AUTH_TYPE);
        requestConfig.setInboundAuthKey(ISSUER_NAME);

        Property attributeConsumerServiceIndexProp = new Property();
        attributeConsumerServiceIndexProp.setName(ATTRIBUTE_CS_INDEX_NAME);
        attributeConsumerServiceIndexProp.setValue(ATTRIBUTE_CS_INDEX_VALUE);
        requestConfig.setProperties(new Property[]{attributeConsumerServiceIndexProp});

        InboundAuthenticationConfig inboundAuthenticationConfig = new InboundAuthenticationConfig();
        inboundAuthenticationConfig.setInboundAuthenticationRequestConfigs(
                new InboundAuthenticationRequestConfig[]{requestConfig});

        serviceProvider.setInboundAuthenticationConfig(inboundAuthenticationConfig);
        applicationManagementServiceClient.updateApplicationData(serviceProvider);
    }

    private void deleteApplication() throws Exception {
        applicationManagementServiceClient.deleteApplication(APPLICATION_NAME);
    }

    private void createUser() {
        log.info("Creating User " + USERNAME);
        try {
            // creating the user
            remoteUSMServiceClient.addUser(USERNAME, PASSWORD,
                    null, getUserClaims(),
                    profileName, true);
        } catch (Exception e) {
            Assert.fail("Error while creating the user", e);
        }

    }

    private void deleteUser() {
        log.info("Deleting User " + USERNAME);
        try {
            remoteUSMServiceClient.deleteUser(USERNAME);
        } catch (Exception e) {
            Assert.fail("Error while deleting the user", e);
        }
    }

    private ClaimValue[] getUserClaims() {
        ClaimValue[] claimValues = new ClaimValue[4];

        ClaimValue firstName = new ClaimValue();
        firstName.setClaimURI(firstNameClaimURI);
        firstName.setValue(NICKNAME);
        claimValues[0] = firstName;

        ClaimValue lastName = new ClaimValue();
        lastName.setClaimURI(lastNameClaimURI);
        lastName.setValue(USERNAME);
        claimValues[1] = lastName;

        ClaimValue email = new ClaimValue();
        email.setClaimURI(emailClaimURI);
        email.setValue(EMAIL);
        claimValues[2] = email;

        ClaimValue mobile = new ClaimValue();
        mobile.setClaimURI(mobileClaimURI);
        mobile.setValue(MOBILE_NO);
        claimValues[3] = mobile;


        return claimValues;
    }

    private static void buildSAMLAuthenticationConfiguration(IdentityProvider fedIdp) {

        FederatedAuthenticatorConfig saml2SSOAuthnConfig =
                new FederatedAuthenticatorConfig();
        saml2SSOAuthnConfig.setName(SMS_OTP);
        saml2SSOAuthnConfig.setDisplayName(SMS_OTP);
        saml2SSOAuthnConfig.setEnabled(true);
        org.wso2.carbon.identity.application.common.model.idp.xsd.Property[] properties = new org.wso2.carbon
                .identity.application.common.model.idp.xsd.Property[5];

        org.wso2.carbon.identity.application.common.model.idp.xsd.Property property = new org.wso2.carbon.identity
                .application.common.model.idp.xsd.Property();

        property.setName("sms_url");
        property.setValue("http://localhost:9855/testing?api_key=123456&api_secret=ACCDRT&from=NEXMO&to=$ctx" +
                ".num&text=$ctx.msg");
        properties[0] = property;

        property = new org.wso2.carbon.identity.application.common.model.idp.xsd.Property();
        property.setName("http_method");
        property.setValue("GET");
        properties[1] = property;


        property = new org.wso2.carbon.identity.application.common.model.idp.xsd.Property();
        property.setName("headers");
        property.setValue("X-Version: 1,Authorization: bearer ********,Accept: application/json,Content-Type: application/json");
        properties[2] = property;


        property = new org.wso2.carbon.identity.application.common.model.idp.xsd.Property();
        property.setName("payload");
        property.setValue("PAYLOAD");
        properties[3] = property;

        property = new org.wso2.carbon.identity.application.common.model.idp.xsd.Property();
        property.setName("http_response");
        property.setValue("200");
        properties[4] = property;


        saml2SSOAuthnConfig.setProperties(properties);

        fedIdp.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{saml2SSOAuthnConfig});

    }

    private void updateSPWithSteps() {
        LocalAndOutboundAuthenticationConfig localAndOutboundAuthenticationConfig = new
                LocalAndOutboundAuthenticationConfig();
        org.wso2.carbon.identity.application.common.model.xsd.IdentityProvider[] fed = new org.wso2.carbon.identity
                .application.common.model.xsd.IdentityProvider[1];


        AuthenticationStep authenticationStep1 = new AuthenticationStep();

        org.wso2.carbon.identity.application.common.model.xsd.IdentityProvider fedidp = new org.wso2.carbon.identity
                .application.common.model.xsd.IdentityProvider();
        fedidp.setDisplayName(SMS_OTP);
        fedidp.setIdentityProviderName(SMS_OTP);

        org.wso2.carbon.identity.application.common.model.xsd.FederatedAuthenticatorConfig
                federatedAuthenticatorConfig = new org.wso2.carbon.identity.application.common.model.xsd
                .FederatedAuthenticatorConfig();
        federatedAuthenticatorConfig.setName(SMS_OTP);
        federatedAuthenticatorConfig.setDisplayName(SMS_OTP);

        org.wso2.carbon.identity.application.common.model.xsd.FederatedAuthenticatorConfig[]
                federatedAuthenticatorConfigs = new org.wso2.carbon.identity.application.common.model.xsd
                .FederatedAuthenticatorConfig[1];
        federatedAuthenticatorConfigs[0] = federatedAuthenticatorConfig;
        fedidp.setFederatedAuthenticatorConfigs(federatedAuthenticatorConfigs);
        fed[0] = fedidp;
        authenticationStep1.setFederatedIdentityProviders(fed);
        authenticationStep1.setStepOrder(2);

        AuthenticationStep authenticationStep2 = new AuthenticationStep();
        authenticationStep2.setSubjectStep(true);
        authenticationStep2.setAttributeStep(true);
        LocalAuthenticatorConfig[] localAuthenticatorConfig = new LocalAuthenticatorConfig[1];
        LocalAuthenticatorConfig localAuthenticatorConfig1 = new LocalAuthenticatorConfig();
        localAuthenticatorConfig1.setName("BasicAuthenticator");
        localAuthenticatorConfig1.setDisplayName("basic");
        localAuthenticatorConfig[0] = localAuthenticatorConfig1;
        authenticationStep2.setLocalAuthenticatorConfigs(localAuthenticatorConfig);
        authenticationStep2.setStepOrder(1);
        localAndOutboundAuthenticationConfig.setAuthenticationType("flow");
        localAndOutboundAuthenticationConfig.setAuthenticationSteps(new AuthenticationStep[]{authenticationStep2,
                authenticationStep1});
        serviceProvider.setLocalAndOutBoundAuthenticationConfig(localAndOutboundAuthenticationConfig);

        try {
            applicationManagementServiceClient.updateApplicationData(serviceProvider);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private SAMLSSOServiceProviderDTO createSsoServiceProviderDTO() {
        SAMLSSOServiceProviderDTO samlssoServiceProviderDTO = new SAMLSSOServiceProviderDTO();
        samlssoServiceProviderDTO.setIssuer("travelocity.com");
        samlssoServiceProviderDTO.setAssertionConsumerUrls(new String[]{"http://localhost:" + TOMCAT_PORT +
                "/travelocity" +
                ".com/home.jsp"});
        samlssoServiceProviderDTO.setDefaultAssertionConsumerUrl("http://localhost:" + TOMCAT_PORT + "/travelocity" +
                ".com/home.jsp");
        samlssoServiceProviderDTO.setAttributeConsumingServiceIndex(ATTRIBUTE_CS_INDEX_VALUE);
        samlssoServiceProviderDTO.setNameIDFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
        samlssoServiceProviderDTO.setDoSignAssertions(true);
        samlssoServiceProviderDTO.setDoSignResponse(true);
        samlssoServiceProviderDTO.setDoSingleLogout(true);

        return samlssoServiceProviderDTO;
    }

    private void changeISConfiguration() throws Exception {

        log.info("Replacing application-authentication.xml setting showAuthFailureReason true");

        String carbonHome = CarbonUtils.getCarbonHome();
        File applicationXML = new File(carbonHome + File.separator + "repository" + File.separator + "conf" + File
                .separator + "identity" + File.separator + "application-authentication.xml");
        File configuredApplicationXML = new File(getISResourceLocation() + File.separator + "saml" + File.separator +
                "application-authentication_sms_otp_enabled.xml");

        serverConfigurationManager = new ServerConfigurationManager(isServer);
        serverConfigurationManager.applyConfigurationWithoutRestart(configuredApplicationXML, applicationXML, true);
        serverConfigurationManager.restartGracefully();
    }

    private void createIDP() throws Exception {
        ssoConfigServiceClient
                .addServiceProvider(createSsoServiceProviderDTO());
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setIdentityProviderName(SMS_OTP);
        buildSAMLAuthenticationConfiguration(identityProvider);
        identityProviderMgtServiceClient.addIdP(identityProvider);
    }

}
