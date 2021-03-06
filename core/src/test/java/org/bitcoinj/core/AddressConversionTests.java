package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.junit.Assert.*;

public class AddressConversionTests {

    @Test
    public void validateCashAddrs() {
        NetworkParameters params = MainNetParams.get();
        String legacyAddressP2PKH = "1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk";
        assertEquals("bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff", CashAddress.fromBase58(params, legacyAddressP2PKH).toString());

        String cashAddressP2PKH = "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff";
        String legacyAddress = LegacyAddress.fromCashAddress(params, cashAddressP2PKH).toString();
        assertEquals("1MyZGR8HpaN8Hot5trvxUNALEW6E1haAuk", legacyAddress);

        String legacyAddressP2SH = "36JVCGHDv8KaT3aMzrkS8Y2Qh2CHm8Z129";
        String cashAddressP2SH = CashAddress.fromBase58(params, legacyAddressP2SH).toString();
        System.out.println(cashAddressP2SH);
        assertEquals("bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz", cashAddressP2SH);

        System.out.println(CashAddress.fromCashAddress(params, "bitcoincash:qrnpf0nqkzgxpvjzqlqcqm4h78sg45zyu54rngdxff").getOutputScriptType());
        System.out.println(CashAddress.fromCashAddress(params, "bitcoincash:pqeft5ukkg9ew2fgt5axxnrq853nc5cuyvvpusv2nz").getOutputScriptType());
    }
}
