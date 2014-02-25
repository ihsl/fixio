/*
 * Copyright 2014 The FIX.io Project
 *
 * The FIX.io Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package fixio.netty.pipeline.client;

import fixio.events.LogonEvent;
import fixio.fixprotocol.*;
import fixio.fixprotocol.session.FixSession;
import fixio.handlers.FixApplication;
import fixio.netty.pipeline.AbstractSessionHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClientSessionHandler extends AbstractSessionHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClientSessionHandler.class);
    private final FixSessionSettingsProvider sessionSettingsProvider;
    private final MessageSequenceProvider messageSequenceProvider;

    public ClientSessionHandler(FixSessionSettingsProvider settingsProvider,
                                MessageSequenceProvider messageSequenceProvider,
                                FixApplication fixApplication) {
        super(fixApplication);
        assert (settingsProvider != null) : "FixSessionSettingsProvider is expected.";
        this.sessionSettingsProvider = settingsProvider;
        this.messageSequenceProvider = messageSequenceProvider;
    }

    public ClientSessionHandler(FixSessionSettingsProvider settingsProvider, FixApplication fixApplication) {
        this(settingsProvider, StatelessMessageSequenceProvider.getInstance(), fixApplication);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FixMessage msg, List<Object> out) throws Exception {
        final FixMessageHeader header = msg.getHeader();
        FixSession session = getSession(ctx);
        if (MessageTypes.LOGON.equals(header.getMessageType())) {
            if (session != null) {
                getLogger().info("Fix Session Established.");
                if (session.checkIncomingSeqNum(header.getMsgSeqNum())) {
                    LogonEvent logonEvent = new LogonEvent(session);
                    out.add(logonEvent);
                    return;
                } else {
                    throw new IllegalStateException("Duplicate Logon Request. Session Already Established.");
                }

            } else {
                throw new IllegalStateException("Duplicate Logon Request. Session Already Established.");
            }
        }
        super.decode(ctx, msg, out);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        getLogger().info("Connection established, starting Client FIX session.");

        FixMessageBuilder logonRequest = createLogonRequest(sessionSettingsProvider);

        FixSession pendingSession = createSession(sessionSettingsProvider);
        setSession(ctx, pendingSession);
        prepareMessageToSend(ctx, pendingSession, logonRequest);

        getLogger().info("Sending Logon: {}", logonRequest);

        ctx.writeAndFlush(logonRequest);
    }

    private static FixMessageBuilderImpl createLogonRequest(FixSessionSettingsProvider sessionSettingsProvider) {
        FixMessageBuilderImpl messageBuilder = new FixMessageBuilderImpl(MessageTypes.LOGON);
        messageBuilder.add(FieldType.HeartBtInt, sessionSettingsProvider.getHeartbeatInterval());
        messageBuilder.add(FieldType.EncryptMethod, 0);
        return messageBuilder;
    }

    private FixSession createSession(FixSessionSettingsProvider settingsProvider) {

        int nextIncomingSeqNum;
        if (settingsProvider.isResetMsgSeqNum()) {
            nextIncomingSeqNum = 1;
        } else {
            nextIncomingSeqNum = messageSequenceProvider.getMsgInSeqNum();
        }

        FixSession session = FixSession.newBuilder()
                .beginString(settingsProvider.getBeginString())
                .senderCompId(settingsProvider.getSenderCompID())
                .senderSubId(settingsProvider.getSenderSubID())
                .targetCompId(settingsProvider.getTargetCompID())
                .targetSubId(settingsProvider.getTargetSubID())
                .nextIncomingSeqNum(nextIncomingSeqNum)
                .build();
        session.setNextOutgoingMessageSeqNum(messageSequenceProvider.getMsgOutSeqNum());
        return session;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}