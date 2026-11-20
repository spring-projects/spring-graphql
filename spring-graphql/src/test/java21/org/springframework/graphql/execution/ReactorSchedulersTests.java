/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.execution;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReactorSchedulersTests {

	@Test
	void singleThreadReturnsVirtualThread() {
		Scheduler scheduler = ReactorSchedulers.singleThread("testThread");
		scheduler.init();
		Disposable disposable = scheduler.schedule(() -> assertThat(Thread.currentThread().isVirtual()).isTrue());
		await().until(disposable::isDisposed);
		scheduler.dispose();
	}

}
