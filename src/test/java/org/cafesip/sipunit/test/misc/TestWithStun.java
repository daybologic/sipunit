/*
 * Created on Nov. 1, 2006
 * 
 * Copyright 2005/2006 CafeSip.org 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *	http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 */
package org.cafesip.sipunit.test.misc;

import java.util.Properties;

import javax.sip.message.Response;

import net.java.stun4j.StunAddress;
import net.java.stun4j.StunException;
import net.java.stun4j.client.NetworkConfigurationDiscoveryProcess;
import net.java.stun4j.client.StunDiscoveryReport;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTestCase;

/**
 * This class tests SipUnit with Stun. It uses a STUN server to find out the
 * public IP address and port it should use when communicating with a SIP server
 * on the internet. Then it creates 2 test SipPhones, registers both caller and
 * callee parties with a public SIP server, makes a call from caller to callee,
 * then the callee disconnects. (IE, the caller and callee user agents
 * (SipPhones) are running via this test class behind your firewall, registering
 * with the public sip server and communicating with each other through the
 * public sip server.)
 * 
 * Thanks to manchi for notes on STUN support and for the example code which is
 * used here (getPublicAddress()).
 * 
 * At a public SIP server you'll need 2 accounts for this test, one for ua (user
 * 'a' in this test) and another for ub (user 'b'). Replace in this file
 * occurences of your-publicserver-account1 and your-publicserver-account2 (and
 * their passwords) with the 2 accounts at the public server.
 * 
 * Also edit the defaultProperties below and substitute your values for:
 * 
 * <pre>
 * - javax.sip.IP_ADDRESS (use your local/internal machine address that your router to the
 * outside world knows about)
 * - stun.server (the address of some STUN server on the internet)
 * - sipunit.test.domain (the domain part of your 2 public SIP account addresses)
 * - sipunit.proxy.host (the host/address of your public SIP server where your accounts are)
 * - sipunit.proxy.port (the SIP port used by your public SIP server if not the default 5060)
 * </pre>
 * 
 * This class uses Stun4j, the OpenSource Java Solution for NAT and Firewall
 * Traversal. It is distributable under the LGPL license. See the terms of
 * license at gnu.org or open the sipunit license file at
 * docs/license/index.html with your browser.
 * 
 * @author Becky McElroy
 * 
 */
public class TestWithStun extends SipTestCase
{
    private SipStack sipStack;

    private SipPhone ua;

    private int proxyPort;

    private int myPort;

    private String testProtocol;

    private String myUrl;

    private String publicIP;

    private int publicPort;

    private static final Properties defaultProperties = new Properties();
    static
    {
//        defaultProperties.setProperty("javax.sip.IP_ADDRESS", "192.168.1.101");
    	defaultProperties.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");
        defaultProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
        defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testAgent_debug.txt");
        defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testAgent_log.txt");
        defaultProperties
                .setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        defaultProperties.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

        defaultProperties.setProperty("sipunit.trace", "true");
        defaultProperties.setProperty("sipunit.test.port", "5060");
        defaultProperties.setProperty("sipunit.test.protocol", "udp");

//        defaultProperties.setProperty("stun.server",
//                "<some-stun-server-address>");
        defaultProperties.setProperty("stun.server",
                "192.168.0.70");
        defaultProperties.setProperty("sipunit.test.domain",
                "<your-public-SIP-accounts-domain>");
        defaultProperties.setProperty("sipunit.proxy.host",
                "<your-public-SIP-server-host>");
        defaultProperties.setProperty("sipunit.proxy.port", "5060");
    }

    private Properties properties = new Properties(defaultProperties);

    public TestWithStun(String arg0)
    {
        super(arg0);

        try
        {
            myPort = Integer.parseInt(properties
                    .getProperty("sipunit.test.port"));
        }
        catch (NumberFormatException e)
        {
            myPort = 5060;
        }

        try
        {
            proxyPort = Integer.parseInt(properties
                    .getProperty("sipunit.proxy.port"));
        }
        catch (NumberFormatException e)
        {
            proxyPort = 5060;
        }

        testProtocol = properties.getProperty("sipunit.test.protocol");
        myUrl = "sip:your-publicserver-account1@"
                + properties.getProperty("sipunit.test.domain");

    }

    /*
     * @see SipTestCase#setUp()
     */
    public void setUp() throws Exception
    {
        // use the stun server to find out my public address
        assertTrue(getPublicAddress());

        try
        {
            sipStack = new SipStack(testProtocol, myPort, properties);
            SipStack.setTraceEnabled(true);
        }
        catch (Exception ex)
        {
            fail("Exception: " + ex.getClass().getName() + ": "
                    + ex.getMessage());
            throw ex;
        }

        SipStack.trace("My public IP address = " + publicIP + ", port = "
                + publicPort);

        try
        {
            ua = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, myUrl);

            ua.setPublicAddress(publicIP, publicPort);
        }
        catch (Exception ex)
        {
            fail("Exception creating SipPhone: " + ex.getClass().getName()
                    + ": " + ex.getMessage());
            throw ex;
        }
    }

    public boolean getPublicAddress()
    {
        StunAddress localAddr = new StunAddress(properties
                .getProperty("javax.sip.IP_ADDRESS"), myPort);
        StunAddress serverAddr = new StunAddress(properties
                .getProperty("stun.server"), 3478);

        NetworkConfigurationDiscoveryProcess addressDiscovery = new NetworkConfigurationDiscoveryProcess(
                localAddr, serverAddr);

        try
        {
            addressDiscovery.start();
        }
        catch (StunException e)
        {
            e.printStackTrace();
            return false;
        }

        StunDiscoveryReport stunReport = null;
        try
        {
            stunReport = addressDiscovery.determineAddress();
        }
        catch (StunException e)
        {
            e.printStackTrace();
            return false;
        }

        StunAddress stunAddress = stunReport.getPublicAddress();

        publicIP = stunAddress.getSocketAddress().getAddress().getHostAddress();
        publicPort = stunAddress.getSocketAddress().getPort();

        // must shutdown stun process, otherwise, stun4j is holding on the same
        // udp port

        addressDiscovery.shutDown();

        return true;
    }

    /*
     * @see SipTestCase#tearDown()
     */
    public void tearDown() throws Exception
    {
        ua.dispose();
        sipStack.dispose();
    }

    public void testCall()
    {
        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"),
                "your-publicserver-account1",
                "your-publicserver-account1-password"));

        try
        {
            SipStack.trace("About to register using credentials ");

            ua.register(null, 3600);

            assertLastOperationSuccess(
                    "Caller registration using pre-set credentials failed - "
                            + ua.format(), ua);

            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:your-publicserver-account2@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"),
                    "your-publicserver-account2",
                    "your-publicserver-account2-password"));

            // set public address on ub
            ub.setPublicAddress(publicIP, publicPort);

            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:your-publicserver-account2@"
                    + properties.getProperty("sipunit.test.domain"), null);

            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            Thread.sleep(1000);

            assertResponseReceived("Should have gotten RINGING response",
                    SipResponse.RINGING, a);

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
            Thread.sleep(1000);

            assertResponseReceived(SipResponse.OK, a);

            assertTrue(a.sendInviteOkAck());
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            assertTrue(b.waitForAck(1000));

            a.listenForDisconnect();
            Thread.sleep(100);

            assertTrue(b.disconnect());
            assertLastOperationSuccess("b disc - " + b.format(), b);

            // verify extra parameters were received in the message

            a.waitForDisconnect(1000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            assertTrue(a.respondToDisconnect());

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

}