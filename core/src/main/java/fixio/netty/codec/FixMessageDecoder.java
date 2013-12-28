/*
 * Copyright 2013 The FIX.io Project
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

package fixio.netty.codec;

import fixio.fixprotocol.SimpleFixMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Decodes series of {@link ByteBuf}s into FixMessages.
 * <p/>
 * <p>
 * Use following code to configure {@link io.netty.channel.ChannelPipeline}:
 * <pre><code>
 * ChannelPipeline pipeline = ch.pipeline();
 * pipeline.addLast("tagDecoder", new DelimiterBasedFrameDecoder(1024, Unpooled.wrappedBuffer(new byte[]{1})));
 * pipeline.addLast("fixMessageDecoder", new FixMessageDecoder());
 * pipeline.addLast("fixMessageEncoder", new FixMessageEncoder());
 * </code></pre>
 * <p/>
 * FixMessageDecoder should be preceded in pipeline with {@link io.netty.handler.codec.DelimiterBasedFrameDecoder}, which is responsible for detecting FIX Protocol tags.
 * </p>
 * <p/>
 * <p>
 * <strong>This class is not thread safe!</strong>
 * It should be a separate instance per {@link io.netty.channel.Channel}.
 * </p>
 */
public class FixMessageDecoder extends MessageToMessageDecoder<ByteBuf> {

    private SimpleFixMessage message;
    private int checksum;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        assert (in != null) : "No Buffer";

        int arrayIndex = 0;
        int tagNum = 0;
        byte[] bytes = in.array();
        int length = bytes.length;
        for (; arrayIndex < length; arrayIndex++) {
            byte b = bytes[arrayIndex];
            if (b == '=') {
                break;
            }
            tagNum = tagNum * 10 + (b - '0');
        }

        String value = new String(bytes, arrayIndex + 1, length - arrayIndex - 1, US_ASCII);

        int sumBytes = 0;
        if (tagNum != 10) {
            for (byte b : bytes) {
                sumBytes += b;
            }
            sumBytes += 1; // SOH value
        }
        switch (tagNum) {
            case 8:
                if (message != null) {
                    throw new DecoderException("Unexpected BeginString(8)");
                }
                message = new SimpleFixMessage();
                checksum = sumBytes;
                message.add(tagNum, value);
                break;
            case 10:
                appendField(tagNum, value);
                verifyChecksum(value);
                out.add(message);
                message = null;
                checksum = 0;
                break;
            default:
                appendField(tagNum, value);
                checksum += sumBytes;
        }
    }

    private void appendField(int tag, String value) {
        if (message == null) {
            throw new DecoderException("BeginString tag expected, but got: " + tag + "=" + value);
        }
        message.add(tag, value);
    }

    private void verifyChecksum(String value) {
        int declaredChecksum = Integer.parseInt(value);
        checksum = (checksum % 256);
        if (declaredChecksum != checksum) {
            message = null;
            throw new DecoderException("Checksum mismatch. Expected: " + declaredChecksum + " but found: " + checksum);
        }
    }
}
