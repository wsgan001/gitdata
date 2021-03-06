package edu.hit.yh.gitdata.mine.algorithm;

import java.io.BufferedWriter;
import java.io.File;
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

import edu.hit.yh.gitdata.githubDataModel.HibernateUtil;
import edu.hit.yh.gitdata.mine.constant.DirConstant;
import edu.hit.yh.gitdata.mine.module.AbstractActorBehavior;
import edu.hit.yh.gitdata.mine.module.Artifact;
import edu.hit.yh.gitdata.mine.module.BehaviorPattern;
import edu.hit.yh.gitdata.mine.module.SimpleBehavior;
import edu.hit.yh.gitdata.mine.module.TimeBasedBehavior;
import edu.hit.yh.gitdata.mine.util.ArtifactUtil;
import edu.hit.yh.gitdata.mine.util.GraphUtil;
import edu.hit.yh.gitdata.mine.util.RelativeTimeUtil;

/**
 * 1、对长度为1的项进行计数，设初始的相对时间为0；
 * 2、对长度为K的项进行连接操作，得到长度为K+1的候选集项，但是这是基于相对时间的序列，得把相对时间考虑进去
 * ，那就是说在生成候选集时，需要再回到artifactlist中考察，得到靠谱的候选项
 * 连接操作的步骤：（1）、比较第一个连接项除去头和第二个连接项去掉尾中间的项是否相同，如果相同则将两项连接
 * 3、对连接项进行支持度计数，计数时，可以把计数项的任意一点作为初始点来进行记录
 * （在每一个统计artifact中附带一个hashmap，负责记录这个项里包不包含我们所有的项）
 * 
 * @author DHAO
 *
 */
public class TimeBasedGspMiningAlgorithm extends
		AbstractGspMiningAlgorithm<BehaviorPattern> {

	/**
	 * 初始化算法类，输入序列的最小支持度
	 * 
	 * @param surpport
	 */
	public TimeBasedGspMiningAlgorithm(int surpport) {
		super.setSurpport(surpport);
	}

	/**
	 * 算法的执行模块
	 * 
	 * 基于相对时间的的GSP执行，扫描数据集合->筛选出长度为K的序列模式->连接长度为K的序列模式生成K+1的候选序列模式->再次扫描数据库->...
	 */
	@Override
	public void execute(Object... args) {

		int nowLength = 1;
		boolean algorithmEndFlag = false;
		List<Artifact<SimpleBehavior>> artifactList = this.buildArtifacts(
				getRepo(), getArtifactType());
		//HibernateUtil.closeSessionFactory();
		List<BehaviorPattern> preBehaviorPatterns = new ArrayList<BehaviorPattern>();
		List<BehaviorPattern> resultBehaviorPatterns = new ArrayList<BehaviorPattern>();
		/**
		 * 如果当前的候选序列中还有behavior则算法继续进行 1、对当前的候选序列，在artifactList中进行扫描，计数
		 * 2、筛选掉surpport小于最小支持度的BehaviorPattern
		 * 3、留下的list继续做连接操作，进而生成新的候选BehaviorPatternList
		 * ，而上一个候选BehaviorPatternList，则加入到result中 4、对第二个序列进行1的操作
		 */
		while (!algorithmEndFlag) {
			if (nowLength == 1) {// 扫描全artifactList来建立数据
				for (int i = 0; i < artifactList.size(); i++) {
					List<SimpleBehavior> behaviorSeq = artifactList.get(i)
							.getBehaviorSeq();
					for (int j = 0; j < behaviorSeq.size(); j++) {
						SimpleBehavior simpleBehavior = behaviorSeq.get(j);
						if (!addBehaviorPatternCount(simpleBehavior,
								preBehaviorPatterns)) {// 如果该行为没有被add过则将该行为的初始相对时间置为null
							BehaviorPattern<TimeBasedBehavior> behaviorPattern = new BehaviorPattern<TimeBasedBehavior>();
							behaviorPattern
									.setBehaviorList(new ArrayList<TimeBasedBehavior>());
							preBehaviorPatterns.add(behaviorPattern);
							TimeBasedBehavior t = new TimeBasedBehavior();
							t.setActor(simpleBehavior.getActor());
							t.setAction(simpleBehavior.getAction());
							t.setCreatedAt(simpleBehavior.getCreatedAt());
							t.setEventType(simpleBehavior.getEventType());
							t.setTarget(simpleBehavior.getTarget());
							t.setRelativeTime("0");
							behaviorPattern.getBehaviorList().add(t);
							behaviorPattern.addSurpport();
						}
					}
				}
				preBehaviorPatterns = this.pruning(preBehaviorPatterns,
						artifactList);
			} else {
				List<BehaviorPattern> tempList = new ArrayList<BehaviorPattern>(
						preBehaviorPatterns);
				resultBehaviorPatterns.addAll(tempList);
				preBehaviorPatterns = new ArrayList<BehaviorPattern>();
				preBehaviorPatterns = this.joinOperation(tempList);
				preBehaviorPatterns = this.pruning(preBehaviorPatterns,
						artifactList);
				if (preBehaviorPatterns.size() == 0) {// 如果找不到候选序列，说明算法结束
					algorithmEndFlag = true;
				}
			}
			nowLength++;
			System.out.println("nowLength" + nowLength);
		}
		/**
		 * 输出结果到本地路径，用作后期分析
		 */

		try {
			File file = new File(DirConstant.PATTERN_RESULT_FOLDER + "TBGM"
					+ "-" + getRepo().replaceAll("/", "-") + getSurpport()
					+ ".txt");
			if (!file.exists()) {
				file.createNewFile();
			}
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
			for (BehaviorPattern<TimeBasedBehavior> bp : resultBehaviorPatterns) {
				List<TimeBasedBehavior> sList = bp.getBehaviorList();
				System.out.print("surpport=" + bp.getSurpport() + " ");
				for (TimeBasedBehavior s : sList) {
					String behavior = s.getActor() + " " + s.getEventType() + " "
							 + s.getRelativeTime() + " |";
					System.out.print(behavior);
					bw.write(behavior);
				}
				System.out.println();
				bw.write("->"+String.valueOf(bp.getSurpport()));
				bw.write("\n");
				bw.flush();
			}
			//GraphUtil.exportTimeBasedGraph(resultBehaviorPatterns, "");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// resultBehaviorPatterns.forEach(System.out::println);
	}

	/**
	 * 判断当前的behavior在不在list中（已完成————未测试）
	 * 扫描当前的preBehaviorPatterns，如果存在，则计数+1，返回true
	 * 如果不在preBehaviorPatterns，则直接返回true
	 * 
	 * @param simpleBehavior
	 * @param preBehaviorPatterns
	 * @return
	 */
	private boolean addBehaviorPatternCount(SimpleBehavior simpleBehavior,
			List<BehaviorPattern> preBehaviorPatterns) {
		for (BehaviorPattern behaviorPattern : preBehaviorPatterns) {
			List<TimeBasedBehavior> list = behaviorPattern.getBehaviorList();
			if (list.get(0).simplEquals(simpleBehavior)) {
				behaviorPattern.addSurpport();
				return true;
			}
		}
		return false;
	}

	/**
	 * 扫描数据库，得到我们在计数阶段所需要的artifactList
	 */
	@Override
	public List<Artifact<SimpleBehavior>> buildArtifacts(String repo,
			String ArtifactType) {
		List<Artifact<SimpleBehavior>> artifactList = null;
		Class clazz = ArtifactUtil.class;
		Method method = null;
		try {
			method = clazz.getMethod("get" + ArtifactType + "SimpleBehavior",
					String.class);
			artifactList = (List<Artifact<SimpleBehavior>>) method.invoke(
					clazz.newInstance(), repo);
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
	 * 对当前的候选集做连接操作
	 * 
	 * @param patternlist
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public List<BehaviorPattern> joinOperation(List<BehaviorPattern> patternlist) {
		System.out.println("进行连接操作");
		List<BehaviorPattern> tempList = new ArrayList<BehaviorPattern>(
				patternlist);
		List<BehaviorPattern> resultList = new ArrayList<BehaviorPattern>();
		Collections.copy(tempList, patternlist);
		Set<String> patternSet = new HashSet<String>();
		for (BehaviorPattern behaviorPattern1 : tempList) {
			for (BehaviorPattern behaviorPattern2 : patternlist) {
				if (tempList.indexOf(behaviorPattern1) != patternlist
						.indexOf(behaviorPattern2)
						&& isAbleToJoin(behaviorPattern1, behaviorPattern2)) {
					List<TimeBasedBehavior> timeBasedBehaviorList1 = behaviorPattern1
							.getBehaviorList();
					List<TimeBasedBehavior> timeBasedBehaviorList2 = behaviorPattern2
							.getBehaviorList();
					List<TimeBasedBehavior> joinList = new ArrayList<TimeBasedBehavior>(
							timeBasedBehaviorList1);
					Collections.copy(joinList, timeBasedBehaviorList1);
					/**
					 * 重新构造一个TimeBasedBehavior,以防引用被篡改
					 */
					TimeBasedBehavior t2 = timeBasedBehaviorList2
							.get(timeBasedBehaviorList2.size() - 1);
					TimeBasedBehavior t3 = new TimeBasedBehavior();
					t3.setActor(new String(t2.getActor()));
					t3.setEventType(new String(t2.getEventType()));
					t3.setRelativeTime(new String(t2.getRelativeTime()));
					t3.setCreatedAt(new String(t2.getCreatedAt()));
					joinList.add(t3);
					List<TimeBasedBehavior> timeBasedBehaviorList3 = joinList;
					/**
					 * 将模式转换成list然后放在set中检查，如果已经存在则不添加该模式
					 */
					String patternString = "";
					for(TimeBasedBehavior tbb:timeBasedBehaviorList3){
						patternString += tbb.getActor()+" ";
						patternString += tbb.getEventType()+" ";
						patternString += tbb.getRelativeTime()+" |"; 
					}
					if(!patternSet.contains(patternString)){//如果set中不存在该模式则该模式是新的
						patternSet.add(patternString);
					BehaviorPattern behaviorPattern = new BehaviorPattern<AbstractActorBehavior>();
					behaviorPattern.setBehaviorList(timeBasedBehaviorList3);
					behaviorPattern.setSurpport(0);
					resultList.add(behaviorPattern);
					System.out.println("成功添加pattern" + behaviorPattern);
					}
				}
			}
		}
		return resultList;
	}

	/**
	 * 判断两个pattern能不能一起join（已完成——未测试）
	 * 
	 * @param behaviorPattern1
	 * @param behaviorPattern2
	 * @return
	 */
	private boolean isAbleToJoin(BehaviorPattern behaviorPattern1,
			BehaviorPattern behaviorPattern2) {
		List<TimeBasedBehavior> timeBasedBehaviorList1 = behaviorPattern1
				.getBehaviorList();
		List<TimeBasedBehavior> timeBasedBehaviorList2 = behaviorPattern2
				.getBehaviorList();

		if (timeBasedBehaviorList1.size() == 1) {
			if (timeBasedBehaviorList1.get(0).equals(
					timeBasedBehaviorList2.get(0))
					&& // 若list长度等于1 并且，二者并不是同时的同一个行为,可以连接
					timeBasedBehaviorList1
							.get(0)
							.getCreatedAt()
							.equals(timeBasedBehaviorList2.get(0)
									.getCreatedAt())) {
				return false;
			} else if (RelativeTimeUtil.calculateRelativeTime(
					timeBasedBehaviorList1.get(0).getCreatedAt(),
					timeBasedBehaviorList2.get(0).getCreatedAt()).equals(
					"unleagle")) {
				return false;
			} else {
				return true;
			}
		}

		/**
		 * 如果长度大于1，则遍历pattern进行比较
		 */
		for (int i = 1; i < timeBasedBehaviorList1.size(); i++) {
			if (i == 1) {
				if (!(timeBasedBehaviorList2.get(i - 1)
						.simplEquals(timeBasedBehaviorList1.get(i)))
				/*
				 * ||RelativeTimeUtil.calculateRelativeTime(timeBasedBehaviorList1
				 * .get(i).getCreatedAt(),
				 * timeBasedBehaviorList2.get(i-1).getCreatedAt
				 * ()).equals("unleagle"))
				 */)
					return false;
			} else if (!timeBasedBehaviorList1.get(i).equals(
					timeBasedBehaviorList2.get(i - 1))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 对当前得到的候选序列进行剪枝，在计数过程中，先把握住行为是否匹配，然后与上一个行为计算相对时间，相对时间匹配的才能确认支持度+1
	 */
	@Override
	public List<BehaviorPattern> pruning(List<BehaviorPattern> patternlist,
			Object artifacts) {
		System.out.println("进行剪枝操作");
		if (patternlist.isEmpty()) {
			return patternlist;
		}
		// 算法所能容忍的最小支持度
		int surpport = this.getSurpport();
		// 算法运行时所需要的原始数据
		@SuppressWarnings("unchecked")
		List<Artifact<SimpleBehavior>> artifactList = (List<Artifact<SimpleBehavior>>) artifacts;
		/**
		 * 一个m*n的时间复杂度的算法，利用基于时间的
		 * 
		 * 这里需要对不同长度的pattern做不同的处理，具体处理内容如下 长度为1时，不考虑，
		 * 长度为2时，在artifactList中寻找其对应的模式得到第二个行为的相对时间
		 * 长度为3及以上时，在pattern的连接上，忽略第二个pattern的第一个相对时间
		 * （或假设为第一个pattern的第二个行为的相对时间），若其余的行为一致，则进行连接
		 */
		if (patternlist.get(0).getBehaviorList().size() == 1) {

		} else if (patternlist.get(0).getBehaviorList().size() == 2) {// 如果长度为2的话，则需要计算模式中的相对时间，才能返回结果
			// 用来存储已经整理出来的带有相对时间的模式
			HashMap<String, BehaviorPattern> prePatternMap = new HashMap<String, BehaviorPattern>();
			for (BehaviorPattern<TimeBasedBehavior> preBehaviorPattern : patternlist) {
				for (Artifact<SimpleBehavior> artifact : artifactList) {
					if (isNeedToCheck(preBehaviorPattern, artifact)) {
						// 用来标记prePattern已经扫描到了第几个
						List<TimeBasedBehavior> preTimePatternList = preBehaviorPattern
								.getBehaviorList();
						List<SimpleBehavior> artBehaviorSeq = artifact
								.getBehaviorSeq();
						int preNum = 0;
						int artNum = 0;
						for (SimpleBehavior simpleBehavior : artBehaviorSeq) {
							if (preTimePatternList.get(preNum).simplEquals(
									simpleBehavior)) {
								if (preNum == 1) {// 说明已经成功匹配到了一个长度为2的模式
									String r = RelativeTimeUtil
											.calculateRelativeTime(
													artBehaviorSeq.get(artNum)
															.getCreatedAt(),
													simpleBehavior
															.getCreatedAt());
									// 为第二个没有相对时间的行为赋予上相对时间
									preTimePatternList.get(preNum)
											.setRelativeTime(r);
									if (!preTimePatternList.get(0)
											.getRelativeTime().equals("0")) {
									}
									StringBuilder patternInfo = new StringBuilder();
									/*
									 * 构造每一个pattern所具有的唯一key
									 * 由三个元素组成，actor,行为类型，相对时间
									 */
									for (TimeBasedBehavior t : preTimePatternList) {
										patternInfo.append(t.getActor() + " ");
										patternInfo.append(t.getEventType()
												+ " ");
										patternInfo.append(t.getRelativeTime()
												+ "|");
									}
									if (prePatternMap.containsKey(patternInfo
											.toString())) {// 如果这个pattern已经被统计过则直接支持度+1
										prePatternMap.get(
												patternInfo.toString())
												.addSurpport();
									} else {// 如果没有被统计过，则新建一个BehaviorPattern，然后支持度置为1
										BehaviorPattern<TimeBasedBehavior> pattern = new BehaviorPattern<TimeBasedBehavior>();
										pattern.setBehaviorList(preTimePatternList);
										pattern.setSurpport(1);
										prePatternMap
												.put(patternInfo.toString(),
														pattern);
									}
								} else {
									artNum = artBehaviorSeq
											.indexOf(simpleBehavior);
									preNum++;
								}
							}
						}
					}
				}
			}
			List<BehaviorPattern> list = new ArrayList<BehaviorPattern>();
			for (Map.Entry<String, BehaviorPattern> entry : prePatternMap
					.entrySet()) {
				if (entry.getValue().getSurpport() >= this.getSurpport()) {
					list.add(entry.getValue());
				}
			}
			return list;
		} else if (patternlist.get(0).getBehaviorList().size() > 2) {// 如果模式长度大于2，则正常计数
			for (BehaviorPattern<TimeBasedBehavior> preBehaviorPattern : patternlist) {
				for (Artifact<SimpleBehavior> artifact : artifactList) {
					if (isNeedToCheck(preBehaviorPattern, artifact)) {// 如果有检查的必要
						int artPoint = 0;
						List<SimpleBehavior> artBehaviorSeq = artifact
								.getBehaviorSeq();
						// 建立与pattern的List长度一样长的resultIndexList，每匹配成功一个行为，则将该行为在artifact中的角标记录下来
						List<Integer> resultIndexList = new ArrayList<Integer>();
						boolean flag = true;
						/**
						 * preSimpleBehavior是待检验的行为序列，
						 * behaviorSeq是artifact中包含的序列
						 */
						for (int preNum = 0; preNum < preBehaviorPattern
								.getBehaviorList().size(); preNum++) {
							TimeBasedBehavior preTimeBehavior = preBehaviorPattern
									.getBehaviorList().get(preNum);
							// 如果还有搜索下去的必要
							if (((artBehaviorSeq.size() - artPoint) >= (preBehaviorPattern
									.getBehaviorList().size() - preNum))
									&& flag) {
								for (; artPoint < artBehaviorSeq.size(); artPoint++) {
									// 这里是验证在当前的artifact中，有没有行为和相对时间都与当前需要验证的行为相符的
									if (preTimeBehavior
											.simplEquals(artBehaviorSeq
													.get(artPoint))) {// 如果在行为上与前者基本一致
										String preRelativeTime = preTimeBehavior
												.getRelativeTime();
										if (preNum != 0) {// 如果验证的是候选行为不是第一个，则需要计算artifact的相对时间，只要行为对上就可以
											System.out.println("preNum"
													+ preNum);
											System.out.println("reSize"
													+ resultIndexList.size());
											int artlastPoint = resultIndexList
													.get(preNum - 1);
											String artifactRealtiveTime = RelativeTimeUtil
													.calculateRelativeTime(
															artBehaviorSeq
																	.get(artlastPoint)
																	.getCreatedAt(),
															artBehaviorSeq
																	.get(artPoint)
																	.getCreatedAt());
											if (!artifactRealtiveTime
													.equals(preRelativeTime)) {// 如果基本行为对不上
												/**
												 * 如果当前没有找到符合preBehaviorPattern的序列
												 * ，则将preNum-1,
												 * 然后将artPoint置为artlastPoint
												 * +1的位置
												 */
												if (artPoint == artBehaviorSeq
														.size() - 1) {
													artPoint = resultIndexList
															.get(preNum - 1) + 1;
													preNum -= 1;
												}
												if (preNum == -1) {// 如果preNum已经不能再减了，说明这个artifact里着实找不到序列了
													flag = false;
													break;
												}
												continue;
											}
										}
										// 到这里是已经找到了相对应的行为了
										if (preNum != preBehaviorPattern
												.getBehaviorList().size() - 1) {// 如果还没有检查完当前behaviorPattern则继续
											if (resultIndexList.size() - 1 < preNum) {
												// 添加0没什么意义，就是让list多一位，以便使用set方法时不出异常
												resultIndexList.add(0);
											}
											resultIndexList.set(preNum,
													artPoint);
											break;
										} else {// 如果扫描成功，则当前的behaviorPattern支持度+1
											preBehaviorPattern.addSurpport();
											System.out
													.println("扫描成功 pattern 的支持度为"
															+ preBehaviorPattern
																	.getSurpport());
											break;
										}
									}
								}
							} else {// 如果已经没有搜索下去的必要
								break;
							}
						}
					} else {

					}
				}
			}

		}
		List<BehaviorPattern> list = new ArrayList<BehaviorPattern>();
		for (BehaviorPattern<SimpleBehavior> behaviorPattern : patternlist) {
			if (behaviorPattern.getSurpport() >= this.getSurpport()) {
				list.add(behaviorPattern);
			}
		}
		return list;
	}

	/**
	 * 检测某个behaviorPattern是否有必要在该artifact中检查
	 * 
	 * @return
	 */
	private boolean isNeedToCheck(
			BehaviorPattern<TimeBasedBehavior> preBehaviorPattern,
			Artifact<SimpleBehavior> artifact) {

		List<TimeBasedBehavior> preBehaviorList = preBehaviorPattern
				.getBehaviorList();
		List<SimpleBehavior> artBehaviorList = artifact.getBehaviorSeq();

		if (preBehaviorList.size() > artBehaviorList.size()) {// 如果检验的行为模式比artifact的长度还要大，那就停止检验
			return false;
		}
		for (TimeBasedBehavior t : preBehaviorList) {
			if (!artifact.getActors().contains(t.getActor())) {
				return false;
			}
		}
		return true;
	}

	public static void main(String args[]) {
		long time1 = System.currentTimeMillis();
		TimeBasedGspMiningAlgorithm timeBasedGspMiningAlgorithm = new TimeBasedGspMiningAlgorithm(150);
		timeBasedGspMiningAlgorithm.setArtifactType("Issue");
		timeBasedGspMiningAlgorithm.setRepo("golang/go/");
		timeBasedGspMiningAlgorithm.execute(null);
		System.out.println("算法结束");
		System.out.println(System.currentTimeMillis() - time1);
		/*timeBasedGspMiningAlgorithm.setSurpport(60);
		timeBasedGspMiningAlgorithm.execute(null);
		timeBasedGspMiningAlgorithm.setSurpport(90);
		timeBasedGspMiningAlgorithm.execute(null);
		timeBasedGspMiningAlgorithm.setSurpport(120);
		timeBasedGspMiningAlgorithm.execute(null);*/
		
	}

}
