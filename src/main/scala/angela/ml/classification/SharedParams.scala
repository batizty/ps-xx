package angela.ml.classification

import org.apache.spark.ml.param._

/**
  * Created by tuoyu on 03/07/2017.
  */

/**
  * Trait for shared param regParam.
  */
private trait HasRegParam extends Params {

  /**
    * Param for regularization parameter (&gt;= 0).
    * @group param
    */
  final val regParam: DoubleParam = new DoubleParam(this, "regParam", "regularization parameter (>= 0)", ParamValidators.gtEq(0))

  /** @group getParam */
  final def getRegParam: Double = $(regParam)
}

/**
  * Trait for shared param maxIter.
  */
private trait HasMaxIter extends Params {

  /**
    * Param for maximum number of iterations (&gt;= 0).
    * @group param
    */
  final val maxIter: IntParam = new IntParam(this, "maxIter", "maximum number of iterations (>= 0)", ParamValidators.gtEq(0))

  /** @group getParam */
  final def getMaxIter: Int = $(maxIter)
}

/**
  * Trait for shared param featuresCol (default: "features").
  */
private trait HasFeaturesCol extends Params {

  /**
    * Param for features column name.
    * @group param
    */
  final val featuresCol: Param[String] = new Param[String](this, "featuresCol", "features column name")

  setDefault(featuresCol, "features")

  /** @group getParam */
  final def getFeaturesCol: String = $(featuresCol)
}

/**
  * Trait for shared param labelCol (default: "label").
  */
private trait HasLabelCol extends Params {

  /**
    * Param for label column name.
    * @group param
    */
  final val labelCol: Param[String] = new Param[String](this, "labelCol", "label column name")

  setDefault(labelCol, "label")

  /** @group getParam */
  final def getLabelCol: String = $(labelCol)
}

/**
  * Trait for shared param predictionCol (default: "prediction").
  */
private trait HasPredictionCol extends Params {

  /**
    * Param for prediction column name.
    * @group param
    */
  final val predictionCol: Param[String] = new Param[String](this, "predictionCol", "prediction column name")

  setDefault(predictionCol, "prediction")

  /** @group getParam */
  final def getPredictionCol: String = $(predictionCol)
}

/**
  * Trait for shared param rawPredictionCol (default: "rawPrediction").
  */
private trait HasRawPredictionCol extends Params {

  /**
    * Param for raw prediction (a.k.a. confidence) column name.
    * @group param
    */
  final val rawPredictionCol: Param[String] = new Param[String](this, "rawPredictionCol", "raw prediction (a.k.a. confidence) column name")

  setDefault(rawPredictionCol, "rawPrediction")

  /** @group getParam */
  final def getRawPredictionCol: String = $(rawPredictionCol)
}

/**
  * Trait for shared param probabilityCol (default: "probability").
  */
private trait HasProbabilityCol extends Params {

  /**
    * Param for Column name for predicted class conditional probabilities. Note: Not all models output well-calibrated probability estimates! These probabilities should be treated as confidences, not precise probabilities.
    * @group param
    */
  final val probabilityCol: Param[String] = new Param[String](this, "probabilityCol", "Column name for predicted class conditional probabilities. Note: Not all models output well-calibrated probability estimates! These probabilities should be treated as confidences, not precise probabilities")

  setDefault(probabilityCol, "probability")

  /** @group getParam */
  final def getProbabilityCol: String = $(probabilityCol)
}

/**
  * Trait for shared param threshold (default: 0.5).
  */
private trait HasThreshold extends Params {

  /**
    * Param for threshold in binary classification prediction, in range [0, 1].
    * @group param
    */
  final val threshold: DoubleParam = new DoubleParam(this, "threshold", "threshold in binary classification prediction, in range [0, 1]", ParamValidators.inRange(0, 1))

  setDefault(threshold, 0.5)

  /** @group getParam */
  def getThreshold: Double = $(threshold)
}


/**
  * Trait for shared param fitIntercept (default: true).
  */
private trait HasFitIntercept extends Params {

  /**
    * Param for whether to fit an intercept term.
    * @group param
    */
  final val fitIntercept: BooleanParam = new BooleanParam(this, "fitIntercept", "whether to fit an intercept term")

  setDefault(fitIntercept, true)

  /** @group getParam */
  final def getFitIntercept: Boolean = $(fitIntercept)
}

/**
  * Trait for shared param handleInvalid.
  */
private trait HasHandleInvalid extends Params {

  /**
    * Param for how to handle invalid entries. Options are skip (which will filter out rows with bad values), or error (which will throw an error). More options may be added later.
    * @group param
    */
  final val handleInvalid: Param[String] = new Param[String](this, "handleInvalid", "how to handle invalid entries. Options are skip (which will filter out rows with bad values), or error (which will throw an error). More options may be added later", ParamValidators.inArray(Array("skip", "error")))

  /** @group getParam */
  final def getHandleInvalid: String = $(handleInvalid)
}

/**
  * Trait for shared param standardization (default: true).
  */
private trait HasStandardization extends Params {

  /**
    * Param for whether to standardize the training features before fitting the model.
    * @group param
    */
  final val standardization: BooleanParam = new BooleanParam(this, "standardization", "whether to standardize the training features before fitting the model")

  setDefault(standardization, true)

  /** @group getParam */
  final def getStandardization: Boolean = $(standardization)
}

/**
  * Trait for shared param seed (default: this.getClass.getName.hashCode.toLong).
  */
private trait HasSeed extends Params {

  /**
    * Param for random seed.
    * @group param
    */
  final val seed: LongParam = new LongParam(this, "seed", "random seed")

  setDefault(seed, this.getClass.getName.hashCode.toLong)

  /** @group getParam */
  final def getSeed: Long = $(seed)
}

/**
  * Trait for shared param elasticNetParam.
  */
private trait HasElasticNetParam extends Params {

  /**
    * Param for the ElasticNet mixing parameter, in range [0, 1]. For alpha = 0, the penalty is an L2 penalty. For alpha = 1, it is an L1 penalty.
    * @group param
    */
  final val elasticNetParam: DoubleParam = new DoubleParam(this, "elasticNetParam", "the ElasticNet mixing parameter, in range [0, 1]. For alpha = 0, the penalty is an L2 penalty. For alpha = 1, it is an L1 penalty", ParamValidators.inRange(0, 1))

  /** @group getParam */
  final def getElasticNetParam: Double = $(elasticNetParam)
}

/**
  * Trait for shared param tol.
  */
private trait HasTol extends Params {

  /**
    * Param for the convergence tolerance for iterative algorithms (&gt;= 0).
    * @group param
    */
  final val tol: DoubleParam = new DoubleParam(this, "tol", "the convergence tolerance for iterative algorithms (>= 0)", ParamValidators.gtEq(0))

  /** @group getParam */
  final def getTol: Double = $(tol)
}

/**
  * Trait for shared param stepSize.
  */
private trait HasStepSize extends Params {

  /**
    * Param for Step size to be used for each iteration of optimization (&gt; 0).
    * @group param
    */
  final val stepSize: DoubleParam = new DoubleParam(this, "stepSize", "Step size to be used for each iteration of optimization (> 0)", ParamValidators.gt(0))

  /** @group getParam */
  final def getStepSize: Double = $(stepSize)
}

/**
  * Trait for shared param weightCol.
  */
private trait HasWeightCol extends Params {

  /**
    * Param for weight column name. If this is not set or empty, we treat all instance weights as 1.0.
    * @group param
    */
  final val weightCol: Param[String] = new Param[String](this, "weightCol", "weight column name. If this is not set or empty, we treat all instance weights as 1.0")

  /** @group getParam */
  final def getWeightCol: String = $(weightCol)
}

/**
  * Trait for shared param solver (default: "auto").
  */
private trait HasSolver extends Params {

  /**
    * Param for the solver algorithm for optimization. If this is not set or empty, default value is 'auto'.
    * @group param
    */
  final val solver: Param[String] = new Param[String](this, "solver", "the solver algorithm for optimization. If this is not set or empty, default value is 'auto'")

  setDefault(solver, "auto")

  /** @group getParam */
  final def getSolver: String = $(solver)
}

/**
  * Trait for shared param aggregationDepth (default: 2).
  */
private trait HasAggregationDepth extends Params {

  /**
    * Param for suggested depth for treeAggregate (&gt;= 2).
    * @group expertParam
    */
  final val aggregationDepth: IntParam = new IntParam(this, "aggregationDepth", "suggested depth for treeAggregate (>= 2)", ParamValidators.gtEq(2))

  setDefault(aggregationDepth, 2)

  /** @group expertGetParam */
  final def getAggregationDepth: Int = $(aggregationDepth)
}

/**
  * Trait for shared param ps server count (default: 10).
  */
private trait ParameterServerCount extends Params {
  /**
    * Param for suggested parameter server for Machine Learning Algorithm(&gt;=2)
    */
  final val parameterServerCount: IntParam = new IntParam(this, "parameterServerCount", "parameter server count", ParamValidators.gtEq(2))

  setDefault(parameterServerCount, 2)

  final def getParameterServerCount: Int = $(parameterServerCount)
}

/**
  * Trait for shared param ps master
  */
private trait ParameterServerMaster extends Params {
  /**
    * Param for Parameter Server Master host and port
    */
  final val parameterServerMaster: Param[String] = new Param[String](
    parent = this,
    name = "parameterServerMaster",
    doc = "Parameter Server Master address. If this is not set or empty, should throw error",
    isValid = { ss: String => ss.nonEmpty && ss != "" })

  final def getMaster: String = $(parameterServerMaster)
}

/**
  * Trait for shared param model path
  */
private trait ModelPath extends Params {
  /**
    * Param for Model Store path including HDFS path
    */
  final val path: Param[String] = new Param[String](
    parent = this,
    name = "path",
    doc = "Model Path for Store Model, including data and parameters.",
    isValid = { ss: String => ss.nonEmpty && ss != "" })

  final def getPath: String = $(path)
}

/**
  * Trait for shared param parameter number
  */
private trait NumOfFeatures extends Params {
  /**
    * Param for model features
    */
  final val numOfFeatures: LongParam = new LongParam(this, "numOfFeatures", "number of features for this model")

  final def getNumOfFeatures: Long = $(numOfFeatures)
}

/**
  *
  */
private trait IsAsynchronousAlgorithm extends Params {
  /**
    * Param for Algorithm whether Asynchronous or not
    */
  final val isAsynchronousAlgorithm: BooleanParam = new BooleanParam(this, "IsAsynchronousAlgorithm", "During iteration, this algorithm is asynchronous or not")

  setDefault(isAsynchronousAlgorithm, true)

  final def IsAsynchronousAlgorithm: Boolean = $(isAsynchronousAlgorithm)
}

/**
  *
  */
private trait TrainDataSetSplitRatio extends Params {
  /**
    * Param for Split a very tiny tests set to show progress during training
    */
  final val splitRatio: DoubleParam = new DoubleParam(this, "splitRatio", "Split out a tiny tests set to show progress during training", ParamValidators.inRange(0.0, 1.0))

  setDefault[Double](splitRatio, 0.0)

  final def getSplitRatio: Double = $(splitRatio)
}

private trait BatchSize extends Params {
  final val batchSize: LongParam = new LongParam(this, "batchSize", "Batch Size for number of samples to update gradient", ParamValidators.gtEq(10L))

  setDefault[Long](batchSize, 1000L)

  final def getBatchSize: Long = $(batchSize)
}

private trait LearningRate extends Params {
  final val learningRate: DoubleParam = new DoubleParam(this, "learningRate", "Learning for gradient for solve LR", ParamValidators.gt(0.001))

  setDefault[Double](learningRate, 1.0)

  final def getLearningRate: Double = $(learningRate)
}

private trait LearningRateDecay extends Params {
  final val learningRateDecay: DoubleParam = new DoubleParam(this, "learningRateDecay", "Decay of Learning Rate", ParamValidators.gt(0.001))

  setDefault[Double](learningRateDecay, 0.5)

  final def getLearningRateDecay: Double = $(learningRateDecay)
}
// scalastyle:on

