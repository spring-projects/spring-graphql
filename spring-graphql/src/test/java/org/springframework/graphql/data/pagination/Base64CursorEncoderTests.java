/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.pagination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Base64CursorEncoder}.
 *
 * @author Rossen Stoyanchev
 */
class Base64CursorEncoderTests {

	private final Base64CursorEncoder encoder = new Base64CursorEncoder();


	@Test
	void encodeAndDecode() {
		testEncodeAndDecode("O_43", "T180Mw==");
		testEncodeAndDecode("K_{\"firstName\":\"Joseph\",\"id\":103}", "S197ImZpcnN0TmFtZSI6Ikpvc2VwaCIsImlkIjoxMDN9");
	}

	private void testEncodeAndDecode(String decoded, String encoded) {
		assertThat(this.encoder.encode(decoded)).isEqualTo(encoded);
		assertThat(this.encoder.decode(encoded)).isEqualTo(decoded);
	}

}
