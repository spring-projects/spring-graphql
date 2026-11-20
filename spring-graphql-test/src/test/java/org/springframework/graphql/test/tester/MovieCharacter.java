/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.test.tester;

import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;


public class MovieCharacter {


	private @Nullable String name;


	public void setName(String name) {
		this.name = name;
	}

	public @Nullable String getName() {
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
