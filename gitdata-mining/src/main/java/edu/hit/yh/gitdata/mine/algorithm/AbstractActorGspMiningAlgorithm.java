package edu.hit.yh.gitdata.mine.algorithm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.spi.DirStateFactory.Result;

import edu.hit.yh.gitdata.githubDataModel.HibernateUtil;
import edu.hit.yh.gitdata.mine.constant.DirConstant;
import edu.hit.yh.gitdata.mine.module.AbstractActorBehavior;
import edu.hit.yh.gitdata.mine.module.Artifact;
import edu.hit.yh.gitdata.mine.module.BehaviorPattern;
import edu.hit.yh.gitdata.mine.module.SimpleBehavior;
import edu.hit.yh.gitdata.mine.util.ArtifactUtil;
import edu.hit.yh.gitdata.mine.util.GraphUtil;

/**
 * 挖掘抽象用户行为的算法
 * 1、先挖掘出只基于动作的行为模式（不包括人）
 * 2、将这些行为模式带回到实际的动作记录中，对实际的人进行抽象的编码
 * 编码具体方式为：由1->n，按照参与人的先后出现次序进行编码
 * 3、对这些被抽象出来的模式进行支持度计数，得到基于抽象用户的行为模式
 * 
 * 算法未完成，链接部分有待改善
 * @author DHAO
 *
 */
public class AbstractActorGspMiningAlgorithm extends
		AbstractGspMiningAlgorithm<BehaviorPattern> {

	
	/**
	 * 初始化算法类，输入序列的最小支持度
	 * 
	 * @param surpport
	 */
	public AbstractActorGspMiningAlgorithm(int surpport) {
		super.setSurpport(surpport);
	}

	/** 
	 * 算法的执行模块
	 *  
	 * 简单的GSP执行，扫描数据集合->筛选出长度为K的序列模式->连接长度为K的序列模式生成K+1的候选序列模式->再次扫描数据库->...
	 */ 
	@SuppressWarnings("rawtypes")
	@Override
	public void execute(Object... args) {

		int nowLength = 1;
		boolean algorithmEndFlag = false;
		List<Artifact<SimpleBehavior>> artifactList = this.buildArtifacts(getRepo(), getArtifactType());
		//HibernateUtil.closeSessionFactory();
		List<BehaviorPattern> preBehaviorPatterns = new ArrayList<BehaviorPattern>();
		List<BehaviorPattern> resultBehaviorPatterns  = new ArrayList<BehaviorPattern>();
		/**
		 * 如果当前的候选序列中还有behavior则算法继续进行
		 * 1、对当前的候选序列，在artifactList中进行扫描，计数
		 * 2、筛选掉surpport小于最小支持度的BehaviorPattern
		 * 3、留下的list继续做连接操作，进而生成新的候选BehaviorPatternList，而上一个候选BehaviorPatternList，则加入到result中
		 * 4、对第二个序列进行1的操作
		 */
		while(!algorithmEndFlag){//如果候选的序列中还有可以连接的项，则继续进行算法
			if(nowLength==1){//如果是第一次来扫描全表的话，因为BehaviorPatternList没有数据,这时需要扫描全ArtifactList
				for(int i = 0;i<artifactList.size();i++){//对Artifact中的每一个behaviorSeq
					System.out.println(i);
					List<SimpleBehavior> behaviorSeq = artifactList.get(i).getBehaviorSeq();
					for(int j = 0;j<behaviorSeq.size();j++){
						if(!addBehaviorPatternCount(behaviorSeq.get(j),preBehaviorPatterns)){//判断当前的行为是否已经统计过
							BehaviorPattern<AbstractActorBehavior> behaviorPattern = new BehaviorPattern<AbstractActorBehavior>();
							behaviorPattern.setBehaviorList(new ArrayList<AbstractActorBehavior>());
							preBehaviorPatterns.add(behaviorPattern);
							AbstractActorBehavior a = new AbstractActorBehavior();
							SimpleBehavior s = behaviorSeq.get(j);
							a.setEventType(s.getEventType());
							a.setAction(s.getAction());
							a.setCreatedAt(s.getCreatedAt());
							behaviorPattern.getBehaviorList().add(a);
							behaviorPattern.addSurpport();
						}
					}
				}
				preBehaviorPatterns = this.pruning(preBehaviorPatterns,artifactList);
			}else {/*如果不是记录长度为1的行为模式，则要把当前的候选序列存入总的result中，
					然后做连接操作，then,对得到的新的候选序列进行计数*/
				List<BehaviorPattern> tempList = new ArrayList<BehaviorPattern>(preBehaviorPatterns);
				if(nowLength>2){
					resultBehaviorPatterns.addAll(tempList);//这里要深拷贝
				}
				preBehaviorPatterns = new ArrayList<BehaviorPattern>();
				preBehaviorPatterns = this.joinOperation(tempList);
				preBehaviorPatterns = this.pruning(preBehaviorPatterns,artifactList);
				if(preBehaviorPatterns.size()==0||nowLength>=7){//如果找不到候选序列了，说明第一步结束了，我们得到了基于纯操作的模式
					algorithmEndFlag = true;
					HashMap<String, Integer> abstractActorsResultList = findAbstractActorBehaviorPatterns(resultBehaviorPatterns,artifactList);
					//打印所有的结果
					try {
						File file = new File(DirConstant.PATTERN_RESULT_FOLDER+"AAGM"+"-"+getRepo().replaceAll("/", "-")+getSurpport()+".txt");
						if(!file.exists()){
							file.createNewFile();
						}
						BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
						
						List<String> printList = new ArrayList<String>();
						for(Map.Entry<String, Integer> entry:abstractActorsResultList.entrySet()){
							if(entry.getValue()>getSurpport()){
								printList.add(entry.getKey());
								System.out.print(entry.getKey()+" ");
								System.out.println(entry.getValue());
								String result = entry.getKey()+"->"+entry.getValue();
								System.out.println(result);
								bw.write(result);
								bw.write("\n");
								bw.flush();
							} 
						}
						bw.close();
						//下面是画图函数将得到的模式结果画在指定的文件夹下
						//GraphUtil.exportAbstractActorGraph(printList, DirConstant.ABSTRACT_GSP_RESULT_DIR);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			nowLength++;
		}
		//resultBehaviorPatterns.forEach(System.out::println);
	}
	
	/**
	 * 判断当前的behavior在不在list中（已完成————未测试）
	 * 扫描当前的preBehaviorPatterns，如果存在，则计数+1，返回true
	 * 如果不在preBehaviorPatterns，则直接返回true
	 * @param simpleBehavior
	 * @param preBehaviorPatterns
	 * @return
	 */
	private boolean addBehaviorPatternCount(SimpleBehavior simpleBehavior,
			List<BehaviorPattern> preBehaviorPatterns) {
		for(BehaviorPattern behaviorPattern:preBehaviorPatterns){
			List<AbstractActorBehavior> list = behaviorPattern.getBehaviorList();
			if(list.get(0).equals(simpleBehavior)){
				behaviorPattern.addSurpport();
				return true;
			}
		}
		return false;
	}
	/**
	 * 对当前的候选patternlist进行剪枝操作（已完成————未测试）
	 * 统计当前的patternlist中每一个behaviorPattern的支持度，不满足最小支持度的全都放弃
	 * 四层循环，略有心虚。。。。。>_<
	 */
	@SuppressWarnings({ "rawtypes", "unchecked", "unused" })
	@Override
	public List<BehaviorPattern> pruning(List<BehaviorPattern> patternlist,Object artifacts) {
		//算法所能容忍的最小支持度
		int surpport = getSurpport();
		//算法执行时所需要的原始数据
		List<Artifact<SimpleBehavior>> artifactList = (List<Artifact<SimpleBehavior>>) artifacts;
		//经过剪枝所得到的最后的结果，作为下次运算的候选序列
		List<BehaviorPattern> preBehaviorPatterns = new ArrayList<BehaviorPattern>(); 
		
		for(BehaviorPattern<AbstractActorBehavior> behaviorPattern:patternlist){//待验证的每一个patternList
			for(Artifact<SimpleBehavior> artifact:artifactList){//验证的总artifact集合
				int point = 0;
				int num = 0;
				List<SimpleBehavior> behaviorSeq = artifact.getBehaviorSeq();
				for(AbstractActorBehavior preSimpleBehavior:behaviorPattern.getBehaviorList()){
					//如果还有搜索下去的必要
					if((behaviorSeq.size()-point)>=
							(behaviorPattern.getBehaviorList().size()-num)){
						for(;point<behaviorSeq.size();point++){
							if(preSimpleBehavior.equals(behaviorSeq.get(point))){
								if(num!=behaviorPattern.getBehaviorList().size()-1){//如果还没有检查完当前behaviorPattern则继续
									num++;
									break;
								}else{//如果扫描成功，则当前的behaviorPattern支持度+1
									behaviorPattern.addSurpport();
								}
							}
						}
					}else{
						break;
					}
					
				}
			}
		}
		
		//List<BehaviorPattern> list = patternlist.stream().filter(o->o.getSurpport()>=this.getSurpport());
		List<BehaviorPattern> list =  new ArrayList<BehaviorPattern>();
		for(BehaviorPattern<AbstractActorBehavior> behaviorPattern:patternlist){
			if(behaviorPattern.getSurpport()>=this.getSurpport()){
				list.add(behaviorPattern);
			}
		}
		return 	list;
	}

	/**
	 * 对现有的pattern做连接操作（已完成——未测试）
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public  List<BehaviorPattern> joinOperation(List<BehaviorPattern> patternlist) {
		List<BehaviorPattern> tempList = new ArrayList<BehaviorPattern>(patternlist);
		List<BehaviorPattern> resultList = new ArrayList<BehaviorPattern>();
		Collections.copy(tempList, patternlist);
		System.out.println("进行连接操作...");
		/**
		 * 用于避免重复的模式存在，优化连接操作
		 */
		Set<String> patternSet = new HashSet<String>();
		for(BehaviorPattern behaviorPattern1:tempList){
			for(BehaviorPattern behaviorPattern2:patternlist){
				if(/*tempList.indexOf(behaviorPattern1)!=patternlist.indexOf(behaviorPattern2)&&*/isAbleToJoin(behaviorPattern1, behaviorPattern2)){//如果两个行为模式是可以join的
					List<AbstractActorBehavior> abstractActorBehaviorList1 =  behaviorPattern1.getBehaviorList();
					List<AbstractActorBehavior> abstractActorBehaviorList2 =  behaviorPattern2.getBehaviorList();
					List<AbstractActorBehavior> joinList = new ArrayList<AbstractActorBehavior>(abstractActorBehaviorList1);
					Collections.copy(joinList, abstractActorBehaviorList1);
					joinList.add(abstractActorBehaviorList2.get(abstractActorBehaviorList2.size()-1));
					String patternString = "";
					for(AbstractActorBehavior aab:joinList){
						patternString+=aab.getEventType()+"|";
					}
					if(patternSet.contains(patternString)){
						continue;
					}else {
						patternSet.add(patternString);
					}
					List<AbstractActorBehavior> abstractActorBehaviorList3 = joinList;
					BehaviorPattern behaviorPattern = new BehaviorPattern<AbstractActorBehavior>();
					behaviorPattern.setBehaviorList(abstractActorBehaviorList3);
					behaviorPattern.setSurpport(0);
					resultList.add(behaviorPattern);
				}
			}
		}
		return resultList;
	}
	/**
	 * 判断两个pattern能不能一起join（已完成——未测试）
	 * @param behaviorPattern1
	 * @param behaviorPattern2
	 * @return
	 */
	private boolean isAbleToJoin(BehaviorPattern behaviorPattern1, BehaviorPattern behaviorPattern2) {
		List<AbstractActorBehavior> abstractActorBehaviorList1 =  behaviorPattern1.getBehaviorList();
		List<AbstractActorBehavior> abstractActorBehaviorList2 =  behaviorPattern2.getBehaviorList();
		
		if(abstractActorBehaviorList1.size()==1){
			/*if(abstractActorBehaviorList1.get(0).equals(abstractActorBehaviorList2.get(0))&&//若list长度等于1 并且，二者并不是同时的同一个行为,可以连接
					abstractActorBehaviorList1.get(0).getCreatedAt().equals(
							abstractActorBehaviorList2.get(0).getCreatedAt())){
				return false;
			}else{
				return true;
			}*/
			return true;
		}
		
		for(int i=1;i<abstractActorBehaviorList1.size();i++){
			if(!abstractActorBehaviorList1.get(i).equals(abstractActorBehaviorList2.get(i-1))){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * 从数据库中取出指定的repo下的某个类型的artifact，这个list是原始的数据，所有的统计支持度都是从这里下手（已完成-未测试）
	 * artifactType 分为：
	 * 1、issue
	 * 2、pullrequest
	 * 3、commit
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Artifact<SimpleBehavior>> buildArtifacts(String repo,
			String ArtifactType) {
        
		List<Artifact<SimpleBehavior>> artifactList = null;
		Class clazz = ArtifactUtil.class;
		Method method = null;
		try {
			method = clazz.getMethod("get" + ArtifactType + "SimpleBehavior",
					String.class);
			artifactList = (List<Artifact<SimpleBehavior>>)method.invoke(clazz.newInstance(), repo);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		}

		return artifactList;
	}
	
	/**
	 * 我们挖掘出的纯基于操作的行为模式与原始数据artifacts,之间进行统计最终得到基于抽象用户的模式，
	 * 存储在一个容器中，然后统计这个容器里支持度高于我们所要求的支持度的项
	 * 
	 * 
	 * 1、查看该artifact中是否有构成该BehaviorPattern的所有操作序列；
	 * 2、若存在，则记录所有构成该BehaviorPattern的次序，比如在一个artifact中，可能1，3；2，5都能组成某个长度为2的这样的行为序列
	 * 3、将在artifact中的实际序列中的行为参与人编号，由先后顺序，编为1->N
	 * 4、null的编码为0
	 * @param patternList
	 * @param artifacts
	 * @return
	 */
	private HashMap<String, Integer>  findAbstractActorBehaviorPatterns(List<BehaviorPattern> patternList,List<Artifact<SimpleBehavior>> artifacts){
		
		HashMap<String, Integer> abstractActorResultMap = new HashMap<String, Integer>();
		int ci=0;
		for(BehaviorPattern<AbstractActorBehavior> aab:patternList){
			if(patternList.indexOf(aab)>5500){
				break;
			}
			ci++;
			System.out.println("第"+ci+"个模式,一共"+patternList.size()+"个模式");
			for(Artifact<SimpleBehavior> artifact:artifacts){
				/*
				 * 得到在这个artifact下满足与当前模式的角标序列
				 */
				List<List<Integer>> resultCombination = getResultCombination(aab,artifact);
				if(resultCombination!=null&&!resultCombination.isEmpty()){
					HashSet<String> combinationSet = new HashSet<String>();
					for(List<Integer> result:resultCombination){
						//将所有的人物编码都存放在一个Map里
						HashMap<String, Integer> encodingMap = new HashMap<String, Integer>();
						Integer encode=1;
						BehaviorPattern<AbstractActorBehavior> aabPattern = new BehaviorPattern<AbstractActorBehavior>();
						List<AbstractActorBehavior> aablist = new ArrayList<AbstractActorBehavior>();
						
						for(Integer i:result){//为每一个行为的发起者和接收者进行编码
							AbstractActorBehavior a = new AbstractActorBehavior();
							aablist.add(a);
							SimpleBehavior sb = artifact.getBehaviorSeq().get(i);
							String actor = sb.getActor();
							if(!encodingMap.containsKey(actor)){
								encodingMap.put(actor, encode++);
							}
							a.setActor(encodingMap.get(actor));
							/*if(sb.getTarget()==null||sb.getTarget().equals("null")){//如果没有接收者则
								encodingMap.put("null", 0);
								a.getTarget().add(encodingMap.get("null"));
							}else {
								String [] tars = sb.getTarget().split(" ");
								for(String s:tars){
									if(!encodingMap.containsKey(s)){
										encodingMap.put(s, encode++);
									}
									a.getTarget().add(encodingMap.get(s));
								}
							}*/
							a.setEventType(sb.getEventType());
							a.setAction(sb.getAction());
						}
						//将转换过来的模式变成字符串，存储在map中，用于后期的计数
						StringBuilder pattern = new StringBuilder();
						for(AbstractActorBehavior a:aablist){
							pattern.append(a.getActor()+"+");
							pattern.append(a.getEventType()+"+");
							/*Collections.sort(a.getTarget());
							for(Integer i:a.getTarget()){
								pattern.append(i+" ");
							}*/
							pattern.append("|");
						}
						//如果在结果Map中找到了匹配的模式，则将其支持度+1
						combinationSet.add(pattern.toString());
					}
					for(String pattern:combinationSet){
						if(abstractActorResultMap.containsKey(pattern)){
							int i = abstractActorResultMap.get(pattern);
							abstractActorResultMap.put(pattern, i+1);
						}else {
							abstractActorResultMap.put(pattern, 1);
						}
					}
				}
			}
		}
		return abstractActorResultMap;
	}
	/**
	 * 给定一个纯操作的模式和artifact，找到artifact中符合BehaviorPattern的解空间
	 * 返回的是一个二维数组，每一行存储着在artifact中符合BehaviorPattern的角标序列
	 * @param aab
	 * @param artifact
	 * @return
	 */
	private List<List<Integer>> getResultCombination(
			BehaviorPattern<AbstractActorBehavior> aab,
			Artifact<SimpleBehavior> artifact) {
		
		if(aab.getBehaviorList().size()>artifact.getBehaviorSeq().size()){
			return null;
		}
		
		List<SimpleBehavior> artBehaviorSeq  = artifact.getBehaviorSeq();
		List<AbstractActorBehavior> aabBehaviorSeq = aab.getBehaviorList();
		List<List<Integer>> resultList = new ArrayList<List<Integer>>();
		
		//用来计数用的list	
		List<Integer> culculateList = new ArrayList<Integer>(aab.getBehaviorList().size());
		culculateList.add(0);
		int cPoint = 0;
		boolean endflag = false;
		
		while(!endflag){
			for(int i=culculateList.get(cPoint);i<artBehaviorSeq.size()-1;i++){
				if(aabBehaviorSeq.get(cPoint).equals(artBehaviorSeq.get(i))){
					culculateList.set(cPoint, i);
					cPoint++;
					if(cPoint==aabBehaviorSeq.size()){//说明在artifact中成功匹配到了一个组合，这个时候可以把当前的组合输出
						//输出结果
						List<Integer> result = new ArrayList<Integer>(culculateList);
						Collections.copy(result, culculateList);
						resultList.add(result);
						cPoint--;
						endflag = true;
					}else{
						//System.out.println(cPoint+" "+aabBehaviorSeq.size()+" "+culculateList.size());
						if(culculateList.size()>1000){
							System.out.println("pause");
						}
						if(culculateList.size()<aab.getBehaviorList().size()){
							culculateList.add(0);
						}
						culculateList.set(cPoint, i+1);
					}
				}
			}
			cPoint--;
			if(cPoint == -1){
				endflag = true;
			}else {
				culculateList.set(cPoint, culculateList.get(cPoint)+1);
			}
		}
		
		return resultList;
	}
	
	public static void main(String args[]){
		long time1 = System.currentTimeMillis();

		AbstractActorGspMiningAlgorithm abstractActorGspMiningAlgorithm = new AbstractActorGspMiningAlgorithm(33);
		abstractActorGspMiningAlgorithm.setArtifactType("Issue");
		abstractActorGspMiningAlgorithm.setRepo("golang/go/");
		abstractActorGspMiningAlgorithm.execute(null);
		System.out.println(System.currentTimeMillis()-time1);
		/*abstractActorGspMiningAlgorithm.setSurpport(129);
		abstractActorGspMiningAlgorithm.execute(null);
		abstractActorGspMiningAlgorithm.setSurpport(170);
		abstractActorGspMiningAlgorithm.execute(null);*/
		
	}
}
