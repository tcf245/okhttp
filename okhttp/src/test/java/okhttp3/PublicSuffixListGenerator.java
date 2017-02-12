/*
 * Copyright (C) 2017 Square, Inc.
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
package okhttp3;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import okhttp3.internal.PublicSuffixDatabase;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.GzipSink;
import okio.Okio;
import okio.Sink;

/**
 * Downloads the public suffix list from https://publicsuffix.org/list/public_suffix_list.dat and
 * transforms the file into an efficient format used by OkHttp.
 *
 * <p>The intent is to use this class to update the list periodically by manually running the main
 * method. This should be run from the top-level okhttp directory.
 *
 * <p>The resulting file is used by {@link PublicSuffixDatabase}.
 */
public final class PublicSuffixListGenerator {
  private static final String OKHTTP_RESOURCE_DIR = "okhttp" + File.separator + "src"
      + File.separator + "main" + File.separator + "resources";

  private static final ByteString EXCEPTION_RULE_MARKER = ByteString.encodeUtf8("!");

  public static void main(String... args) throws IOException {
    OkHttpClient client = new OkHttpClient.Builder().build();
    Request request = new Request.Builder()
        .url("https://publicsuffix.org/list/public_suffix_list.dat")
        .build();
    Response response = client.newCall(request).execute();

    SortedSet<ByteString> sortedRules = new TreeSet<>();
    SortedSet<ByteString> sortedExceptionRules = new TreeSet<>();
    BufferedSource source = response.body().source();
    int totalRuleBytes = 0;
    int totalExceptionRuleBytes = 0;
    while (!source.exhausted()) {
      String line = source.readUtf8LineStrict();
      if (line.trim().isEmpty() || line.startsWith("//")) continue;

      ByteString rule = ByteString.encodeUtf8(line);
      if (rule.startsWith(EXCEPTION_RULE_MARKER)) {
        rule = rule.substring(1);
        // We use '\n' for end of value.
        totalExceptionRuleBytes += rule.size() + 1;
        sortedExceptionRules.add(rule);
      } else {
        totalRuleBytes += rule.size() + 1; // We use '\n' for end of value.
        sortedRules.add(rule);
      }
    }

    File resources = new File(OKHTTP_RESOURCE_DIR);
    if (!resources.mkdirs() && !resources.exists()) {
      throw new RuntimeException("Unable to create resource directory!");
    }

    Sink fileSink = Okio.sink(new File(resources, PublicSuffixDatabase.PUBLIC_SUFFIX_RESOURCE));
    try (BufferedSink sink = Okio.buffer(new GzipSink(fileSink))) {
      sink.writeInt(totalRuleBytes);
      for (ByteString domain : sortedRules) {
        sink.write(domain).writeByte('\n');
      }

      sink.writeInt(totalExceptionRuleBytes);
      for (ByteString domain : sortedExceptionRules) {
        sink.write(domain).writeByte('\n');
      }
    }
  }
}
