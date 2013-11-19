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

package fixio.netty.pipeline.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertyFixSessionSettingsProviderImpl implements FixSessionSettingsProvider {

    private final Properties properties;

    public PropertyFixSessionSettingsProviderImpl(String resource) {
        properties = new Properties();
        load(resource);
    }

    private void load(String resource) {
        try {
            final InputStream inputStream = getClass().getResourceAsStream(resource);
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getMsgSeqNum() {
        return Integer.parseInt(properties.getProperty("MsgSeqNum", "1"));
    }

    @Override
    public String getSenderCompID() {
        return properties.getProperty("SenderCompID");
    }

    @Override
    public String getSenderSubID() {
        return properties.getProperty("SenderSubID");
    }

    @Override
    public String getTargetCompID() {
        return properties.getProperty("TargetCompID");
    }

    @Override
    public String getTargetSubID() {
        return properties.getProperty("TargetSubID");
    }

    @Override
    public String getBeginString() {
        return properties.getProperty("BeginString");
    }
}