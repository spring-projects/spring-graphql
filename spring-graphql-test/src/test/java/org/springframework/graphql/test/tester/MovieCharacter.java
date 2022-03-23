/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.test.tester;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;


public class MovieCharacter {

	@Nullable
	private String name;


	public void setName(String name) {
		this.name = name;
	}

	@Nullable
	public String getName() {
		return this.name;
	}


	public static MovieCharacter create(String name) {
		MovieCharacter character = new MovieCharacter();
		character.setName(name);
		return character;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(this.name, ((MovieCharacter) other).name);
	}

	@Override
	public int hashCode() {
		return (this.name != null) ? this.name.hashCode() : super.hashCode();
	}

	@Override
	public String toString() {
		return "MovieCharacter[name='" + this.name + "']";
	}

}
