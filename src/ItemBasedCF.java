import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;


public class ItemBasedCF {
	public int k;
	public String trainFile; 
	public String testFile;
	public String outputFile;

	public ItemBasedCF(int k, String trainFile, String testFile, String outputFile){
		this.k = k;
		this.trainFile = trainFile;
		this.testFile = testFile;
		this.outputFile = outputFile;
	}
	
	public void generateRecommendations(String similarityType, String predictionType) throws IOException{
		
		assert(similarityType.equals("dotProduct") || similarityType.equals("cosine") || similarityType.equals("normalizedDotProduct") || similarityType.equals("normalizedCosine"));
		assert(predictionType.equals("mean") || predictionType.equals("weighted"));
		
		System.out.println("\nItem-based CF using "+predictionType+" and "+similarityType+" similarity for top "+this.k+" neighbors");
		
		//Read training file and map data, indexing by item
		System.out.println("reading training data (indexing by item) ...");
		HashMap<Long, Double> itemsAvgRating = new HashMap<Long, Double>();
		HashMap<Long, LinkedList<InteractionInfoUser>> trainItemUserInteractionMap = FileInteraction.readAndMapTrainFileIndexedByItem (trainFile);
		
		//reload train file, but now indexed by user
		System.out.println("reading training data (indexing by user) ...");
		HashMap<Long, LinkedList<InteractionInfoItem>> trainUserItemInteractionMap = FileInteraction.readAndMapTrainFileIndexedByUser(trainFile);
		
		//Read test file and map data, indexing by item
		System.out.println("reading testing data ...");
		HashMap<Long, LinkedList<Long>> testItemUserMap = FileInteraction.readAndMapTestFile(testFile, false);
		
		
		//pre-compute items' average ratings
		for(Long item : trainItemUserInteractionMap.keySet()){
			double sumOfRatings = 0d;
			for(InteractionInfoUser iiu : trainItemUserInteractionMap.get(item)){
				sumOfRatings += iiu.rating;
			}
			double avgRating = sumOfRatings / trainItemUserInteractionMap.get(item).size();
			itemsAvgRating.put(item, avgRating);
		}
		
		// For each user in test set, compute user-user similarity with previous users
		// and get top K users for each of these test users
		System.out.println("doing item-item similiarity recommendation ...");
		
		int count = 0;
		
		//compute prediction for each user and item in the test file
		HashMap<Long, HashMap<Long, Double>> ratingPredictions = new HashMap<Long, HashMap<Long, Double>>(); 
		for(long testItem: testItemUserMap.keySet()){
			
			//get top items for this item
			LinkedList<InteractionInfoUser> testItemFV = trainItemUserInteractionMap.get(testItem);
			PriorityQueue<IdSimilarityPair> topNeighbors = new PriorityQueue<IdSimilarityPair>();
			for(long neighborItem : trainItemUserInteractionMap.keySet()){
				if(testItem == neighborItem) continue;
				
				LinkedList<InteractionInfoUser> neighborItemFV = trainItemUserInteractionMap.get(neighborItem);
				
				//compute similarity
				double similarity = 0d;
				double testItemAvgRating = itemsAvgRating.get(testItem);
				double neighborItemAvgRating = itemsAvgRating.get(neighborItem);
				
				if(similarityType.equals("dotProduct")){
					similarity = ItemSimilarity.dotProductSimilarity(testItemFV, testItemAvgRating, neighborItemFV, neighborItemAvgRating);
				}
				else if(similarityType.equals("cosine")){
					similarity = ItemSimilarity.cosineSimilarity(testItemFV, testItemAvgRating, neighborItemFV, neighborItemAvgRating);
				}
				else if(similarityType.equals("normalizedDotProduct")){
					similarity = ItemSimilarity.normalizedDotProductSimilarity(testItemFV, testItemAvgRating, neighborItemFV, neighborItemAvgRating);
				}
				else if(similarityType.equals("normalizedCosine")){
					similarity = ItemSimilarity.normalizedCosineSimilarity(testItemFV, testItemAvgRating, neighborItemFV, neighborItemAvgRating);
				}
				
				IdSimilarityPair pair = new IdSimilarityPair(neighborItem, similarity);
				//if good enough, add similarity to PriorityQueue
				if(topNeighbors.size() < this.k){
					topNeighbors.add(pair);
				}
				else{
					if(topNeighbors.peek().similarity < similarity){
						topNeighbors.poll();
						topNeighbors.add(pair);
					}
				}
			}
			
			//put top neighbors in list
			LinkedList<IdSimilarityPair> topNeighborsList = new LinkedList<IdSimilarityPair>();
			while(!topNeighbors.isEmpty()){
				topNeighborsList.add(topNeighbors.poll());
			}
			
			//Compute prediction rating for required items
			HashMap<Long, Double> currentItemPredictions = new HashMap<Long, Double>();
			for(long userId : testItemUserMap.get(testItem) ){
				
				double itemRatingPrediction = 0d;
				if(predictionType.equals("mean")){
					itemRatingPrediction = RatingPrediction.itemBasedSimpleAveragePrediction(userId, topNeighborsList, trainUserItemInteractionMap);
				}
				else if(predictionType.equals("weighted")){
					itemRatingPrediction = RatingPrediction.itemBasedWeightedAveragePrediction(userId, topNeighborsList, trainUserItemInteractionMap);
				}
				currentItemPredictions.put(userId, itemRatingPrediction);

				//System.out.println("user: "+userId+" item: "+testItem+" predicition: "+itemRatingPrediction);
			}
			
			ratingPredictions.put(testItem, currentItemPredictions);
			
			count++;
			if(count%500==0) System.out.println(count+"/"+testItemUserMap.size());	
		}
		
		//write predictions to output file in same order
		System.out.println("writing predictions to file... ");
		FileInteraction.writeInvertedPredictionsToFile(testFile, outputFile, ratingPredictions);
	}
	
	public void printItemsTopKNeighbors(long targetItem, String similarityType) throws FileNotFoundException{
		//Read training file and map data, indexing by item
		System.out.println("Top "+this.k+" Neighbors for Movie "+targetItem+" using "+similarityType+" similarity");
		HashMap<Long, LinkedList<InteractionInfoUser>> trainItemUserInteractionMap = FileInteraction.readAndMapTrainFileIndexedByItem (trainFile);
		
		//pre-compute items' average ratings
		HashMap<Long, Double> itemsAvgRating = new HashMap<Long, Double>();
		for(Long item : trainItemUserInteractionMap.keySet()){
			double sumOfRatings = 0d;
			for(InteractionInfoUser iiu : trainItemUserInteractionMap.get(item)){
				sumOfRatings += iiu.rating;
			}
			double avgRating = sumOfRatings / trainItemUserInteractionMap.get(item).size();
			itemsAvgRating.put(item, avgRating);
		}
		
		
		//get top items for this item
		LinkedList<InteractionInfoUser> testItemFV = trainItemUserInteractionMap.get(targetItem);
		PriorityQueue<IdSimilarityPair> topNeighbors = new PriorityQueue<IdSimilarityPair>();
		for(long neighborItem : trainItemUserInteractionMap.keySet()){
			if(targetItem == neighborItem) continue;
			
			LinkedList<InteractionInfoUser> neighborItemFV = trainItemUserInteractionMap.get(neighborItem);
			
			//compute similarity
			double similarity = 0d;
			double targetItemAvgRating = itemsAvgRating.get(targetItem);
			double neighborItemAvgRating = itemsAvgRating.get(neighborItem);
			
			if(similarityType.equals("dotProduct")){
				similarity = ItemSimilarity.dotProductSimilarity(testItemFV, targetItemAvgRating, neighborItemFV, neighborItemAvgRating);
			}
			else if(similarityType.equals("cosine")){
				similarity = ItemSimilarity.cosineSimilarity(testItemFV, targetItemAvgRating, neighborItemFV, neighborItemAvgRating);
			}
			else if(similarityType.equals("normalizedDotProduct")){
				similarity = ItemSimilarity.normalizedDotProductSimilarity(testItemFV, targetItemAvgRating, neighborItemFV, neighborItemAvgRating);
			}
			else if(similarityType.equals("normalizedCosine")){
				similarity = ItemSimilarity.normalizedCosineSimilarity(testItemFV, targetItemAvgRating, neighborItemFV, neighborItemAvgRating);
			}
			
			IdSimilarityPair pair = new IdSimilarityPair(neighborItem, similarity);
			//if good enough, add similarity to PriorityQueue
			if(topNeighbors.size() < this.k){
				topNeighbors.add(pair);
			}
			else{
				if(topNeighbors.peek().similarity < similarity){
					topNeighbors.poll();
					topNeighbors.add(pair);
				}
			}
		}
		
		//put top K neighbors in desc order in array
		int pos = Math.min(this.k, topNeighbors.size());
		IdSimilarityPair[] topK = new IdSimilarityPair[pos];
		while(!topNeighbors.isEmpty()){
			topK[--pos] = topNeighbors.poll();
		}
		for(IdSimilarityPair neighbor : topK){
			System.out.println("id: "+neighbor.id + " - similarity: "+neighbor.similarity);
		}
	}
}
