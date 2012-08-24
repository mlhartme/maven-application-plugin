/**
 * Copyright 1&1 Internet AG, http://www.1and1.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.beezle.maven.plugins.application.data;

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
