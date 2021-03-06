package org.bitcoinj.net;

import com.github.kiulian.converter.AddressConverter;
import org.bitcoinj.core.*;
import org.bitcoinj.core.bip47.BIP47PaymentCode;
import org.bitcoinj.crypto.HashHelper;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.JSONHelper;
import org.bitcoinj.wallet.SendRequest;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class NetHelper {
    private String[] cashAcctServers = new String[]{
            "https://cashacct.imaginary.cash",
            "https://cashaccounts.bchdata.cash",
            "https://cashacct.electroncash.dk"
    };

    private String[] blockExplorers = new String[]{
            "rest.bitcoin.com"
    };

    private String[] blockExplorerAPIURL = new String[]{
            "https://rest.bitcoin.com/v2/transaction/details/"
    };

    public String getCashAccountAddress(NetworkParameters params, String cashAccount)
    {
        return this.getCashAccountAddress(params, cashAccount, false);
    }

    public String getCashAccountAddress(NetworkParameters params, String cashAccount, boolean forceCashAddr)
    {
        String[] splitAccount = cashAccount.split("#");
        String username = splitAccount[0];
        String block;

        if(cashAccount.contains("."))
        {
            String[] splitBlock = splitAccount[1].split("\\.");
            block = splitBlock[0];
        }
        else {
            block = splitAccount[1];
        }
        String txHex = getTxHexFromCashAcct(cashAccount);

        if(txHex != null) {
            Transaction decodedTx = new Transaction(params, Hex.decode(txHex));

            String txid = decodedTx.getHashAsString();
            int txHeight = getTransactionHeight(txid);
            int blockInt = Integer.parseInt(block);
            int cashAccountGenesis = 563620;
            if (blockInt == (txHeight - cashAccountGenesis)) {
                String address = "";
                String blockHash = getTransactionsBlockHash(txid);
                String collision = new HashHelper().getCashAccountCollision(blockHash, txid);
                ArrayList<String> expectedAddresses = getExpectedCashAccountAddresses(username + "#" + block + "." + collision);
                ArrayList<String> addresses = getAddressesFromOpReturn(decodedTx);
                for (String s : addresses) {
                    byte[] hash160 = Hex.decode(s.substring(2));
                    if(forceCashAddr) {
                        if (!Address.isValidPaymentCode(hash160)) {
                            LegacyAddress legacyAddress = LegacyAddress.fromPubKeyHash(params, hash160);
                            address = AddressConverter.toCashAddress(legacyAddress.toBase58());
                        }
                    } else {
                        if (Address.isValidPaymentCode(hash160)) {
                            address = new BIP47PaymentCode(hash160).toString();
                            break;
                        } else {
                            LegacyAddress legacyAddress = LegacyAddress.fromPubKeyHash(params, hash160);
                            address = AddressConverter.toCashAddress(legacyAddress.toBase58());
                        }
                    }
                }

                if (expectedAddresses.indexOf(address) != -1)
                    return address;
                else
                    return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public String getCashAccountAddress(NetworkParameters params, String cashAccount, Proxy proxy)
    {
        String[] splitAccount = cashAccount.split("#");
        String username = splitAccount[0];
        String block;

        if(cashAccount.contains("."))
        {
            String[] splitBlock = splitAccount[1].split("\\.");
            block = splitBlock[0];
        }
        else {
            block = splitAccount[1];
        }
        String txHex = getTxHexFromCashAcct(cashAccount, proxy);
        if(txHex != null) {
            Transaction decodedTx = new Transaction(params, Hex.decode(txHex));

            String txid = decodedTx.getHashAsString();
            int txHeight = getTransactionHeight(txid, proxy);
            int blockInt = Integer.parseInt(block);
            int cashAccountGenesis = 563620;
            if (blockInt == (txHeight - cashAccountGenesis)) {
                String address = "";
                String blockHash = getTransactionsBlockHash(txid, proxy);
                String collision = new HashHelper().getCashAccountCollision(blockHash, txid);
                ArrayList<String> expectedAddresses = getExpectedCashAccountAddresses(username + "#" + block + "." + collision, proxy);
                ArrayList<String> addresses = getAddressesFromOpReturn(decodedTx);
                for (String s : addresses) {
                    String hash160 = s.substring(2);
                    if (Address.isValidPaymentCode(Hex.decode(hash160))) {
                        address = new BIP47PaymentCode(Hex.decode(hash160)).toString();
                        break;
                    } else {
                        LegacyAddress legacyAddress = LegacyAddress.fromPubKeyHash(params, Hex.decode(hash160));
                        address = AddressConverter.toCashAddress(legacyAddress.toBase58());
                    }
                }

                if (expectedAddresses.indexOf(address) != -1)
                    return address;
                else
                    return "Unexpected Cash Account. Server possibly hacked.";
            } else {
                return "Unexpected Cash Account. Server possibly hacked.";
            }
        } else {
            return "Cash Account not found.";
        }
    }

    private String getTransactionsBlockHash(String transactionHash)
    {
        int randExplorer = new Random().nextInt(blockExplorers.length);
        String blockExplorer = blockExplorers[randExplorer];
        String blockExplorerURL = blockExplorerAPIURL[randExplorer];

        String block = "";
        String txHash = transactionHash.toLowerCase();
        InputStream is = null;
        try {
            is = new URL(blockExplorerURL + txHash).openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = JSONHelper.readJSONFile(rd);
            JSONObject json = new JSONObject(jsonText);
            if(blockExplorer.equals("rest.bitcoin.com")) {
                block = json.getString("blockhash");
            }

        } catch (JSONException e) {
            block = "???";
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return block.equals("-1") ? "???" : block;
    }

    private String getTransactionsBlockHash(String transactionHash, Proxy proxy)
    {
        int randExplorer = new Random().nextInt(blockExplorers.length);
        String blockExplorer = blockExplorers[randExplorer];
        String blockExplorerURL = blockExplorerAPIURL[randExplorer];

        String block = "";
        String txHash = transactionHash.toLowerCase();
        InputStream is = null;
        try {
            is = new URL(blockExplorerURL + txHash).openConnection(proxy).getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = JSONHelper.readJSONFile(rd);
            JSONObject json = new JSONObject(jsonText);
            if(blockExplorer.equals("rest.bitcoin.com")) {
                block = json.getString("blockhash");
            }

        } catch (JSONException e) {
            block = "???";
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return block.equals("-1") ? "???" : block;
    }

    private String getTxHexFromCashAcct(String cashAccount) {
        int randExplorer = (new Random()).nextInt(cashAcctServers.length);
        String lookupServer = cashAcctServers[randExplorer];
        String txHex = "";
        String[] splitAccount = cashAccount.split("#");
        String name = splitAccount[0];
        String block = splitAccount[1];

        if(!lookupServer.contains("rest.bitcoin.com"))
        {
            if (!block.contains(".")) {
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/lookup/" + block + "/" + name)).openStream();
                } catch (IOException var56) {
                    var56.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    txHex = json.getJSONArray("results").getJSONObject(0).getString("transaction");
                } catch (JSONException | IOException | NullPointerException var53) {
                    var53.printStackTrace();
                    txHex = null;
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var47) {
                        var47.printStackTrace();
                    }

                }
            } else {
                String[] splitBlock = block.split("\\.");
                String mainBlock = splitBlock[0];
                String collision = splitBlock[1];
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/lookup/" + mainBlock + "/" + name + "/" + collision)).openStream();
                } catch (IOException var52) {
                    var52.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    txHex = json.getJSONArray("results").getJSONObject(0).getString("transaction");
                } catch (JSONException | IOException | NullPointerException var49) {
                    var49.printStackTrace();
                    txHex = null;
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var48) {
                        var48.printStackTrace();
                    }

                }
            }
        }

        return txHex;
    }

    private String getTxHexFromCashAcct(String cashAccount, Proxy proxy) {
        int randExplorer = (new Random()).nextInt(cashAcctServers.length);
        String lookupServer = cashAcctServers[randExplorer];
        String txHex = "";
        String[] splitAccount = cashAccount.split("#");
        String name = splitAccount[0];
        String block = splitAccount[1];

        if(!lookupServer.contains("rest.bitcoin.com"))
        {
            if (!block.contains(".")) {
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/lookup/" + block + "/" + name)).openConnection(proxy).getInputStream();
                } catch (IOException var56) {
                    var56.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    txHex = json.getJSONArray("results").getJSONObject(0).getString("transaction");
                } catch (JSONException | IOException var53) {
                    var53.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var47) {
                        var47.printStackTrace();
                    }

                }
            } else {
                String[] splitBlock = block.split("\\.");
                String mainBlock = splitBlock[0];
                String collision = splitBlock[1];
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/lookup/" + mainBlock + "/" + name + "/" + collision)).openConnection(proxy).getInputStream();
                } catch (IOException var52) {
                    var52.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    txHex = json.getJSONArray("results").getJSONObject(0).getString("transaction");
                } catch (JSONException | IOException var49) {
                    var49.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var48) {
                        var48.printStackTrace();
                    }

                }
            }
        }

        return txHex;
    }

    private ArrayList<String> getAddressesFromOpReturn(Transaction tx)
    {
        ArrayList<String> addresses = new ArrayList<String>();
        List<TransactionOutput> outputs = tx.getOutputs();
        for (TransactionOutput output : outputs) {
            boolean isOpReturn = output.getScriptPubKey().isOpReturn();

            if (isOpReturn) {
                int startingAddressChunk = 3;
                int chunksLength = output.getScriptPubKey().getChunks().size();
                int addressesAmount = chunksLength - startingAddressChunk;

                for(int x = 0; x < addressesAmount; x++) {
                    String address = new String(Hex.encode(Objects.requireNonNull(output.getScriptPubKey().getChunks().get(x + startingAddressChunk).data)), StandardCharsets.UTF_8);

                    if(!address.isEmpty())
                        addresses.add(address);
                }
                break;
            }
        }

        return addresses;
    }

    private int getTransactionHeight(String transactionHash)
    {
        int randExplorer = new Random().nextInt(blockExplorers.length);
        String blockExplorer = blockExplorers[randExplorer];
        String blockExplorerURL = blockExplorerAPIURL[randExplorer];

        int height = 0;
        String txHash = transactionHash.toLowerCase();
        InputStream is = null;
        try {
            is = new URL(blockExplorerURL + txHash).openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = JSONHelper.readJSONFile(rd);
            JSONObject json = new JSONObject(jsonText);
            if(blockExplorer.equals("rest.bitcoin.com")) {
                height = json.getInt("blockheight");
            }

        } catch (JSONException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return height;
    }

    private int getTransactionHeight(String transactionHash, Proxy proxy)
    {
        int randExplorer = new Random().nextInt(blockExplorers.length);
        String blockExplorer = blockExplorers[randExplorer];
        String blockExplorerURL = blockExplorerAPIURL[randExplorer];

        int height = 0;
        String txHash = transactionHash.toLowerCase();
        InputStream is = null;
        try {
            is = new URL(blockExplorerURL + txHash).openConnection(proxy).getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = JSONHelper.readJSONFile(rd);
            JSONObject json = new JSONObject(jsonText);
            if(blockExplorer.equals("rest.bitcoin.com")) {
                height = json.getInt("blockheight");
            }

        } catch (JSONException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return height;
    }

    private ArrayList<String> getExpectedCashAccountAddresses(String cashAccount) {
        int randExplorer = (new Random()).nextInt(cashAcctServers.length);
        String lookupServer = cashAcctServers[randExplorer];
        ArrayList<String> addresses = new ArrayList<String>();
        String[] splitAccount = cashAccount.split("#");
        String name = splitAccount[0];
        String block = splitAccount[1];

        if(!lookupServer.contains("rest.bitcoin.com")) {
            if (!block.contains(".")) {
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/account/" + block + "/" + name)).openStream();
                } catch (IOException var56) {
                    var56.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    int paymentsLength = json.getJSONObject("information").getJSONArray("payment").length();
                    for(int x = 0; x < paymentsLength; x++) {
                        addresses.add(json.getJSONObject("information").getJSONArray("payment").getJSONObject(x).getString("address"));
                    }
                } catch (JSONException | IOException var53) {
                    var53.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var48) {
                        var48.printStackTrace();
                    }

                }
            } else {
                String[] splitBlock = block.split("\\.");
                String mainBlock = splitBlock[0];
                String collision = splitBlock[1];
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/account/" + mainBlock + "/" + name + "/" + collision)).openStream();
                } catch (IOException var52) {
                    var52.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    int paymentsLength = json.getJSONObject("information").getJSONArray("payment").length();
                    for(int x = 0; x < paymentsLength; x++) {
                        addresses.add(json.getJSONObject("information").getJSONArray("payment").getJSONObject(x).getString("address"));
                    }
                } catch (JSONException | IOException var49) {
                    var49.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var47) {
                        var47.printStackTrace();
                    }

                }
            }
        }

        return addresses;
    }

    private ArrayList<String> getExpectedCashAccountAddresses(String cashAccount, Proxy proxy) {
        int randExplorer = (new Random()).nextInt(cashAcctServers.length);
        String lookupServer = cashAcctServers[randExplorer];
        ArrayList<String> addresses = new ArrayList<String>();
        String[] splitAccount = cashAccount.split("#");
        String name = splitAccount[0];
        String block = splitAccount[1];

        if(!lookupServer.contains("rest.bitcoin.com")) {
            if (!block.contains(".")) {
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/account/" + block + "/" + name)).openConnection(proxy).getInputStream();
                } catch (IOException var56) {
                    var56.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    int paymentsLength = json.getJSONObject("information").getJSONArray("payment").length();
                    for(int x = 0; x < paymentsLength; x++) {
                        addresses.add(json.getJSONObject("information").getJSONArray("payment").getJSONObject(x).getString("address"));
                    }
                } catch (JSONException | IOException var53) {
                    var53.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var48) {
                        var48.printStackTrace();
                    }

                }
            } else {
                String[] splitBlock = block.split("\\.");
                String mainBlock = splitBlock[0];
                String collision = splitBlock[1];
                InputStream is = null;

                try {
                    is = (new URL(lookupServer + "/account/" + mainBlock + "/" + name + "/" + collision)).openConnection(proxy).getInputStream();
                } catch (IOException var52) {
                    var52.printStackTrace();
                }

                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = org.bitcoinj.utils.JSONHelper.readJSONFile(rd);
                    JSONObject json = new JSONObject(jsonText);
                    int paymentsLength = json.getJSONObject("information").getJSONArray("payment").length();
                    for(int x = 0; x < paymentsLength; x++) {
                        addresses.add(json.getJSONObject("information").getJSONArray("payment").getJSONObject(x).getString("address"));
                    }
                } catch (JSONException | IOException var49) {
                    var49.printStackTrace();
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException var47) {
                        var47.printStackTrace();
                    }

                }
            }
        }

        return addresses;
    }
}
