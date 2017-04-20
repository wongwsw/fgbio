/*
 * The MIT License
 *
 * Copyright (c) 2017 Fulcrum Genomics LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.fulcrumgenomics.umi

import java.nio.file.Paths

import com.fulcrumgenomics.FgBioDef._
import com.fulcrumgenomics.testing.SamRecordSetBuilder.{Minus, Plus}
import com.fulcrumgenomics.testing.{SamRecordSetBuilder, UnitSpec}
import htsjdk.samtools.SamReaderFactory

class CallDuplexConsensusReadsTest extends UnitSpec {
  private val MI = ConsensusTags.MolecularId

  "CallDuplexConsensusReads" should "throw an exception if the input file doesn't exist" in {
    an[Throwable] should be thrownBy {
      new CallDuplexConsensusReads(input=Paths.get("/tmp/path/to/no/where/foo.bam"), output=Paths.get("/tmp")).execute()
    }
  }

  it should "throw an exception if the output file isn't writable" in {
    an[Throwable] should be thrownBy {
      val in = makeTempFile("in.", ".bam")
      val out = Paths.get("/tmp/path/to/no/where.bam")
      new CallDuplexConsensusReads(input=in, output=out).execute()
    }
  }

  it should "throw an exception if either error rate is set too low" in {
      val in  = makeTempFile("in.", ".bam")
      val out = makeTempFile("out.", ".bam")
    an[Exception] should be thrownBy { new CallDuplexConsensusReads(input=in, output=out, errorRatePreUmi=0.toByte).execute() }
    an[Exception] should be thrownBy { new CallDuplexConsensusReads(input=in, output=out, errorRatePostUmi=0.toByte).execute() }
  }

  it should "have working CLP and arg annotations" in {
    checkClpAnnotations[CallDuplexConsensusReads]
  }

  it should "run fail if AB-R1s are not on the same strand ads BA-R2s" in {
    val builder = new SamRecordSetBuilder(readLength=10)
    builder.addPair(name="ab1", start1=100, start2=200, attrs=Map(MI -> "1/A")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ba1", start1=200, start2=100, attrs=Map(MI -> "1/B")).foreach { _.setReadString("AAAAAAAAAA") }

    val in  = builder.toTempFile()
    val out = makeTempFile("duplex.", ".bam")
    an[Exception] should be thrownBy new CallDuplexConsensusReads(input=in, output=out, readGroupId="ZZ").execute()
  }

  it should "run successfully and create consensus reads" in {
    val builder = new SamRecordSetBuilder(readLength=10)
    builder.addPair(name="ab1", start1=100, start2=100, attrs=Map(MI -> "1/A")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ab2", start1=100, start2=100, attrs=Map(MI -> "1/A")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ab3", start1=100, start2=100, attrs=Map(MI -> "1/A")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ba1", start1=100, start2=100, strand1=Minus, strand2=Plus, attrs=Map(MI -> "1/B")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ba2", start1=100, start2=100, strand1=Minus, strand2=Plus, attrs=Map(MI -> "1/B")).foreach { _.setReadString("AAAAAAAAAA") }
    builder.addPair(name="ba3", start1=100, start2=100, strand1=Minus, strand2=Plus, attrs=Map(MI -> "1/B")).foreach { _.setReadString("AAAAAAAAAA") }

    val in  = builder.toTempFile()
    val out = makeTempFile("duplex.", ".bam")
    new CallDuplexConsensusReads(input=in, output=out, readGroupId="ZZ").execute()
    val reader = SamReaderFactory.make().open(out)
    val recs = reader.toSeq

    reader.getFileHeader.getReadGroups should have size 1
    reader.getFileHeader.getReadGroups.iterator().next().getId shouldBe "ZZ"
    recs should have size 2
  }
}
