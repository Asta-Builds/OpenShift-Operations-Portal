#!/usr/bin/env python3
"""Checks for what kubeconform cannot see. Used by validate.sh.

  validate.py policy-objects FILE...   prints the objects the ACM Policies in FILE create, hub templates replaced,
                                       for kubeconform
  validate.py tekton FILE...           checks Pipelines and PipelineRuns: Tekton's CRDs accept any spec, so a wrong
                                       parameter or workspace name would only fail on the cluster
"""
import re
import sys

import yaml

HUB_TEMPLATE = re.compile(r"\{\{hub .*? hub\}\}")
REFERENCE = re.compile(r"\$\((params|workspaces)\.([A-Za-z0-9_-]+)(?:\.(path|bound|claim|volume))?\)")


def documents(paths):
    for path in paths:
        with open(path) as f:
            for doc in yaml.safe_load_all(f):
                if doc:
                    yield path, doc


def replace_templates(value):
    if isinstance(value, dict):
        return {k: replace_templates(v) for k, v in value.items()}
    if isinstance(value, list):
        return [replace_templates(v) for v in value]
    if isinstance(value, str) and "{{hub" in value:
        return HUB_TEMPLATE.sub("placeholder", value)
    return value


def policy_objects(paths):
    objects = []
    for _, doc in documents(paths):
        if doc.get("kind") != "Policy":
            continue
        for template in doc["spec"].get("policy-templates", []):
            definition = template["objectDefinition"]
            for object_template in definition.get("spec", {}).get("object-templates", []):
                objects.append(replace_templates(object_template["objectDefinition"]))
    if not objects:
        sys.exit("no objects found in the Policies")
    yaml.safe_dump_all(objects, sys.stdout, sort_keys=False)


def references(value):
    """Every $(params.x) and $(workspaces.y...) used anywhere in value."""
    found = set()
    if isinstance(value, dict):
        for v in value.values():
            found |= references(v)
    elif isinstance(value, list):
        for v in value:
            found |= references(v)
    elif isinstance(value, str):
        found |= {(kind, name) for kind, name, _ in REFERENCE.findall(value)}
    return found


def check_pipeline(pipeline, errors):
    name = pipeline["metadata"]["name"]
    spec = pipeline["spec"]
    params = {p["name"] for p in spec.get("params", [])}
    workspaces = {w["name"] for w in spec.get("workspaces", [])}
    tasks = {t["name"] for t in spec.get("tasks", [])}
    for task in spec.get("tasks", []):
        where = f"Pipeline {name}, task {task['name']}"
        ref = task.get("taskRef")
        if ref and ref.get("kind") == "ClusterTask":
            errors.append(f"{where}: ClusterTask no longer exists in OpenShift Pipelines 1.17+")
        for after in task.get("runAfter", []):
            if after not in tasks:
                errors.append(f"{where}: runAfter {after} is not a task of the pipeline")
        for p in task.get("params", []):
            for kind, ref_name in references(p.get("value")):
                if kind == "params" and ref_name not in params:
                    errors.append(f"{where}: param {p['name']} uses undeclared pipeline param {ref_name}")
        for w in task.get("workspaces", []):
            if w["workspace"] not in workspaces:
                errors.append(f"{where}: workspace {w['name']} binds undeclared pipeline workspace {w['workspace']}")
        task_spec = task.get("taskSpec")
        if task_spec is None:
            continue
        declared_params = {p["name"]: p for p in task_spec.get("params", [])}
        declared_workspaces = {w["name"]: w for w in task_spec.get("workspaces", [])}
        given_params = {p["name"] for p in task.get("params", [])}
        given_workspaces = {w["name"] for w in task.get("workspaces", [])}
        for p_name, p in declared_params.items():
            if p_name not in given_params and "default" not in p:
                errors.append(f"{where}: task param {p_name} has no value and no default")
        for p_name in given_params - declared_params.keys():
            errors.append(f"{where}: param {p_name} is not declared by the task")
        for w_name, w in declared_workspaces.items():
            if w_name not in given_workspaces and not w.get("optional"):
                errors.append(f"{where}: task workspace {w_name} is not bound")
        for kind, ref_name in references(task_spec.get("steps", [])):
            if kind == "params" and ref_name not in declared_params:
                errors.append(f"{where}: steps use undeclared task param {ref_name}")
            if kind == "workspaces" and ref_name not in declared_workspaces:
                errors.append(f"{where}: steps use undeclared task workspace {ref_name}")
    return params, workspaces, {w["name"] for w in spec.get("workspaces", []) if not w.get("optional")}


def tekton(paths):
    errors = []
    pipelines = {}
    runs = []
    for path, doc in documents(paths):
        if not doc.get("apiVersion", "").startswith("tekton.dev/"):
            continue
        if doc["apiVersion"] != "tekton.dev/v1":
            errors.append(f"{path}: {doc['kind']} uses {doc['apiVersion']}; use tekton.dev/v1")
        if doc["kind"] == "Pipeline":
            pipelines[doc["metadata"]["name"]] = check_pipeline(doc, errors)
        elif doc["kind"] == "PipelineRun":
            runs.append((path, doc))
    for path, run in runs:
        ref = run["spec"].get("pipelineRef", {}).get("name")
        if ref not in pipelines:
            errors.append(f"{path}: PipelineRun refers to unknown pipeline {ref}")
            continue
        params, workspaces, required = pipelines[ref]
        for p in run["spec"].get("params", []):
            if p["name"] not in params:
                errors.append(f"{path}: PipelineRun sets undeclared param {p['name']}")
        given = {w["name"] for w in run["spec"].get("workspaces", [])}
        for w in given - workspaces:
            errors.append(f"{path}: PipelineRun binds undeclared workspace {w}")
        for w in required - given:
            errors.append(f"{path}: PipelineRun does not bind required workspace {w}")
    if not pipelines:
        errors.append("no Pipeline found")
    for error in errors:
        print(f"ERROR {error}", file=sys.stderr)
    if errors:
        sys.exit(1)
    print(f"Tekton: {len(pipelines)} pipeline(s), {len(runs)} run(s) consistent")


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("policy-objects", "tekton"):
        sys.exit(__doc__)
    {"policy-objects": policy_objects, "tekton": tekton}[sys.argv[1]](sys.argv[2:])
