/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.subset;

import org.mule.extension.file.common.api.subset.SubsetList;
import org.mule.runtime.extension.api.annotation.dsl.xml.ParameterDsl;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

/**
 * Used to obtain a subset of all the files retrieved.
 *
 * @since 1.4.0
 */
public class LocalSubsetList implements SubsetList {

  @Parameter
  @Optional(defaultValue = "0")
  @DisplayName("Limit")
  @ParameterDsl(allowReferences = false)
  @Summary("Limits the number of files returned by the operation.")
  private Integer limit;

  @Parameter
  @Optional(defaultValue = "0")
  @DisplayName("Offset")
  @ParameterDsl(allowReferences = false)
  @Summary("Specifies how many files are skipped.")
  private Integer offset;

  @Parameter
  @Optional(defaultValue = "DATE_MODIFIED")
  @DisplayName("Criteria")
  @ParameterDsl(allowReferences = false)
  @Summary("The criteria according to which files are sorted.")
  protected ListComparator criteria;

  @Parameter
  @Optional(defaultValue = "DESCENDING")
  @DisplayName("Order")
  @ParameterDsl(allowReferences = false)
  @Summary("Specifies whether the order is ASCENDING or DESCENDING according to the criteria.")
  protected SortOrder order;


  public LocalSubsetList() {}

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Integer getOffset() {
    return offset;
  }

  public void setOffset(Integer offset) {
    this.offset = offset;
  }

  public ListComparator getCriteria() {
    return criteria;
  }

  public void setCriteria(ListComparator criteria) {
    this.criteria = criteria;
  }

  public SortOrder getOrder() {
    return order;
  }

  public void setOrder(SortOrder order) {
    this.order = order;
  }

}


