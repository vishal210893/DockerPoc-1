# 🚀 How to Create a Custom JavaScript GitHub Action

Easily automate your workflows by building a custom GitHub Action with JavaScript and npm.

---

## 🛠️ Prerequisites

| Requirement      | Description                                  |
|------------------|----------------------------------------------|
| Node.js (v20+)   | [Download & Install](https://nodejs.org/)    |
| npm (v10+)       | Comes with Node.js                           |
| GitHub Account   | For repository and workflow management        |

---

## 1️⃣ Scaffold Your Action Directory

- Create a new directory for your action:
    - `mkdir -p .github/actions/my-action`
    - `cd .github/actions/my-action`
- Initialize a new npm project:
    - `npm init -y`

---

## 2️⃣ Install Dependencies

- Install the required packages:
    - `npm install @actions/core @actions/exec`

---

## 3️⃣ Add Action Metadata

- Create an `action.yaml` file to define your action’s name, description, inputs, outputs, and entry point.

---

## 4️⃣ Build the Action

- If using TypeScript or modern JavaScript, bundle your code:
    - `npm install --save-dev @vercel/ncc`
    - `npx ncc build index.js -o dist`
- For plain JavaScript, copy your file:
    - `mkdir -p dist`
    - `cp index.js dist/index.js`

---

## 5️⃣ Commit and Push

- Add, commit, and push your changes:
    - `git add .`
    - `git commit -m "Add custom JS action"`
    - `git push`

---

## 6️⃣ Use Your Action in a Workflow

- Reference your action in a workflow YAML file under `.github/workflows/`.

---

## 📋 Summary Table

| Step         | Command/Action                                 |
|--------------|------------------------------------------------|
| Init         | `npm init -y`                                  |
| Install      | `npm install @actions/core @actions/exec`      |
| Build        | `npx ncc build index.js -o dist` (if needed)   |
| Commit       | `git add . && git commit -m "..."`             |
| Use in CI    | Reference in workflow YAML                     |

---

## 🌟 Tips

- Always commit the `dist` folder for GitHub Actions.
- Test your action locally before pushing.
- Use [@vercel/ncc](https://github.com/vercel/ncc) to bundle dependencies for compatibility.

---

## 🧩 Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [JavaScript Actions Toolkit](https://github.com/actions/toolkit)