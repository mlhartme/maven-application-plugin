/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.application.data;

public class Normal {
    public static void foo() {
        Used.ping();
    }

    public static void argument() {
        argumentHelper(null);
    }
    public static void argumentHelper(Used used) {
    }

    public static Used result() {
        return null;
    }

    public static void exception() throws Ex {
    }

    public static Object cast() {
        Object obj = null;
        return (Used) obj;
    }

    public static void catchBuiltIn() {
        try {
            int a = 0;
        } catch (RuntimeException e) {
        }
    }

    public static void catchRex() {
        try {
            int a = 0;
        } catch (Rex e) {
        }
    }



    public static void variable() {
        Used u = null;
    }
}
