/*
 * Copyright 2026 Axel Howind - axh@dua3.com
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
// Copyright (c) 2019 Axel Howind
//
// This software is released under the MIT License.
// https://opensource.org/licenses/MIT

package org.slb4j.support;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A class that provides a shared view of a portion of a base string, implementing
 * the {@link CharSequence} interface. This allows efficient sharing of substrings
 * without copying the underlying character data.
 */
public final class SharedString implements CharSequence {

    private final String base;
    private final int start;
    private final int end;
    private int hash = 0;

    SharedString(String base, int start, int end) {
        this.base = base;
        this.start = Objects.checkIndex(start, base.length());
        Objects.checkFromToIndex(start, end, base.length());
        this.end = end;
    }

    @Override
    public int length() {
        return end - start;
    }

    @Override
    public char charAt(int index) {
        return base.charAt(start + Objects.checkIndex(index, end));
    }

    @Override
    public SharedString subSequence(int start, int end) {
        if (!(end >= start && this.start + end <= this.end)) {
            throw new IndexOutOfBoundsException(end < start ? "end < start" : "end > length");
        }
        return new SharedString(base, this.start + start, this.start + end);
    }

    @Override
    public String toString() {
        return base.substring(start, end);
    }

    @SuppressWarnings("NonFinalFieldReferencedInHashCode")
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && end != start) {
            int len = end - start;
            for (int i = 0; i < len; i++) {
                //noinspection CharUsedInArithmeticContext - by design
                h = 31 * h + base.charAt(start + i);
            }
            hash = h;
        }
        return h;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SharedString anotherString)) {
            return false;
        }

        int n = length();
        if (n == anotherString.length()) {
            for (int i = 0; i < n; i++) {
                if (anotherString.charAt(i) != charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
