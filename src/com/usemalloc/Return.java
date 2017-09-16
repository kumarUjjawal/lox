package com.usemalloc;

import com.sun.org.apache.regexp.internal.RE;

/**
 * Created by ujjawalpathak on 05/08/17.
 */
public class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null,null,false,false);
        this.value = value;
    }
}
