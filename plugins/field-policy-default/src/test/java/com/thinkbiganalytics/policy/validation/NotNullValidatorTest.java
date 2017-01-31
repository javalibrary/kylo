package com.thinkbiganalytics.policy.validation;

/*-
 * #%L
 * thinkbig-field-policy-default
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
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
 * #L%
 */

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * test the {@link NotNullValidator}
 */
public class NotNullValidatorTest {

    @Test
    public void testValidate() throws Exception {
        NotNullValidator validator = new NotNullValidator(false, true);
        assertTrue(validator.validate("foo"));
        assertFalse(validator.validate(""));
        assertFalse(validator.validate("   "));
        assertFalse(validator.validate(null));

        validator = new NotNullValidator(true, true);
        assertTrue(validator.validate("foo"));
        assertTrue(validator.validate(""));
        assertTrue(validator.validate("   "));
        assertFalse(validator.validate(null));

        validator = new NotNullValidator(true, false);
        assertTrue(validator.validate("foo"));
        assertTrue(validator.validate(""));
        assertTrue(validator.validate("   "));
        assertFalse(validator.validate(null));

    }
}
