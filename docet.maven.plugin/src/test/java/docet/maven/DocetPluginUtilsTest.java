/*
 * Copyright 2020 enrico.olivelli.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docet.maven;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class DocetPluginUtilsTest {
   
    @Test
    public void testIndexDocsForLanguage() throws Exception {
        String text = "<html><div>test<b>bold</b></div>friend";
        // use JSoup
        assertEquals("testboldfriend", DocetPluginUtils.convertDocToText("test.html", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        // use Tika
        assertEquals("testbold\nfriend", DocetPluginUtils.convertDocToText("test.unknown.markup", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
    }

}
