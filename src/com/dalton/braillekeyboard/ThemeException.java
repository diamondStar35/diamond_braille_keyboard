/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

/**
 * A sound theme could not be installed, exported or copied.
 *
 * <p>Carries the string resource the user should be shown, so the reason a
 * theme was refused survives the trip from the code that discovered it to the
 * screen that reports it. The exception message stays technical and goes to
 * the diagnostic log; the resource is what the user reads.
 */
class ThemeException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The message to show the user. */
    final int messageResource;

    ThemeException(int messageResource, String detail) {
        super(detail);
        this.messageResource = messageResource;
    }

    ThemeException(int messageResource, String detail, Throwable cause) {
        super(detail, cause);
        this.messageResource = messageResource;
    }
}
