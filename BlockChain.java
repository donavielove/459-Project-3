import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;


public class BlockChain {
   public static final int CUT_OFF_AGE = 10;
   private TransactionPool txPool = new TransactionPool();;
   private BlockNode maxHeightNode;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
         this.uPool = uPool;
         if (parent != null) {
            height = parent.height + 1;
            parent.children.add(this);
         } else {
            height = 1;
         }
      }

      public UTXOPool getUTXOPoolCopy() {
         return new UTXOPool(uPool);
      }
   }

 
   public BlockChain(Block genesisBlock) {

		TransactionPool txnPool = new TransactionPool();
      	UTXOPool uPool = new UTXOPool();
		
      // add  coinbase transaction to global transaction pool
		Transaction coinBaseTx = genesisBlock.getCoinbase();
		txnPool.addTransaction(coinBaseTx);

		int numOpOfCoinBase = coinBaseTx.numOutputs();
		for (int i = 0; i < numOpOfCoinBase; i++) {
			// get the transaction hash
			byte[] coinBaseTxHash = coinBaseTx.getHash();

			// create UTXO
			UTXO utxo = new UTXO(coinBaseTxHash, i);

			// get coinbase transaction output
			Transaction.Output txOut = coinBaseTx.getOutput(i);

			// add new utxo into UXTOPool
			uPool.addUTXO(utxo, txOut);
		}

      		// process rest of block
		ArrayList<Transaction> txns = genesisBlock.getTransactions();
		for (Transaction tx : txns) {
			if (tx != null) {
				// track transaction output history
				int numOpOfCurrTX = tx.numOutputs();
				for (int i = 0; i < numOpOfCurrTX; i++) {
					
					// transaction hash
					byte[] txHash = tx.getHash();

					// create UTXO
					UTXO utxo = new UTXO(txHash, i);

					// get transaction output
					Transaction.Output txOut = tx.getOutput(i);

					// add unspent coin to UTXOPool
					uPool.addUTXO(utxo, txOut);
				}
				// add transaction to global transaction pool
				txnPool.addTransaction(tx);
			}
		}

		maxHeightNode = new BlockNode(genesisBlock, null, uPool);

   }


   	// find the BlockNode that holds the max height
	public BlockNode getMaxheighBlockNode() {		
		int maxHeight = maxHeightNode.height;
		BlockNode RE = maxHeightNode;
		for (BlockNode b: maxHeightNode.children) {
			if (maxHeight < b.height) {
				maxHeight = b.height;
				RE = b;
			}
		}
		
		return RE;
	}
    
    // Get the maximum height block
   public Block getMaxHeightBlock() {
		return getMaxheighBlockNode().b;
   }

    //Get UTXOPool to mine a new block on top of max height block
   public UTXOPool getMaxHeightUTXOPool() {
		return getMaxheighBlockNode().uPool;
   }

    // mine a new block
   public TransactionPool getTransactionPool() {
      return txPool;
   }

   	// helper function for addBlock()
	public BlockNode getParent(byte[] bHash) {
		ByteArrayWrapper arr1 = new ByteArrayWrapper(bHash);
		Stack<BlockNode> s = new Stack<BlockNode>();
		s.add(maxHeightNode);
		Set<BlockNode> visited = new HashSet<BlockNode>();
		while (!s.isEmpty()) {
			BlockNode curr = s.pop();
			byte[] currBlockHash = curr.b.getHash();
			ByteArrayWrapper arr2 = new ByteArrayWrapper(currBlockHash);
			if (arr1.equals(arr2))
				return curr;
			if (!curr.children.isEmpty()) {
				for (BlockNode b : curr.children) {
					if (!visited.contains(b)) {
						s.add(b);
						visited.add(b);
					}
				}
			}
		}

		return null;
	}

   /*
    * Add a block to block chain if it is valid. For validity, all transactions
    * should be valid and block should be at height > (maxHeight - CUT_OFF_AGE).
    * For example, you can try creating a new block over genesis block (block
    * height 2) if blockChain height is <= CUT_OFF_AGE + 1. As soon as height >
    * CUT_OFF_AGE + 1, you cannot create a new block at height 2. Return true of
    * block is successfully added
    */

   public boolean addBlock(Block b) {

      if (b.getPrevBlockHash() == null)
      return false;

      BlockNode prevBlockHash = getParent(b.getPrevBlockHash());

      // If previous block hash is empty return false
      if (prevBlockHash == null)
         return false;

         int nHeight = prevBlockHash.height;
         int maxHeight = getMaxheighBlockNode().height;
         
         if (nHeight < maxHeight - CUT_OFF_AGE)
			return false;
         
         if (maxHeight > CUT_OFF_AGE + 1 && nHeight + 1 == 2) {
            System.out.println(maxHeight);
            return false;
         }	

		// check for all transactions' validity 
		ArrayList<Transaction> txns = b.getTransactions();

		UTXOPool uPool = new UTXOPool(prevBlockHash.getUTXOPoolCopy());
		for (Transaction tx : txns) {
			TxHandler checkTxn = new TxHandler(uPool);
			if (checkTxn.isValidTx(tx) == false)
				return false;


         for (Transaction.Input in : tx.getInputs()) {
               byte[] txHash = in.prevTxHash;
               int index = in.outputIndex;
               UTXO utxo = new UTXO(txHash, index);
               uPool.removeUTXO(utxo);
         }

         for (int i = 0; i < tx.numOutputs(); i++) {
				UTXO utxo = new UTXO(tx.getHash(), i);
				uPool.addUTXO(utxo, tx.getOutput(i));
			}

        	txPool.removeTransaction(tx.getHash());
		}

      Transaction coinBaseTx = b.getCoinbase();
		int numOpOfCoinBaseTx = coinBaseTx.numOutputs();
		for (int i = 0; i < numOpOfCoinBaseTx; i++) {
			byte[] coinBaseTxHash = coinBaseTx.getHash();
			UTXO utxo = new UTXO(coinBaseTxHash, i);
			uPool.addUTXO(utxo, coinBaseTx.getOutput(i));
		}

      
		// add new block 
		BlockNode newBlock = new BlockNode(b, prevBlockHash, uPool);
		maxHeightNode.children.add(newBlock);
		return true;

   }

   //Add transaction
   public void addTransaction(Transaction tx) {
      txPool.addTransaction(tx);
   }

}

