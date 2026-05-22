<?php

namespace App\Service;

use App\Contract\ServiceInterface;

class EmailService extends AbstractService implements ServiceInterface
{
    private string $sender;
    public string $lastError = '';

    public function __construct(string $name, string $sender)
    {
        parent::__construct($name);
        $this->sender = $sender;
    }

    public function execute(string $input): string
    {
        if (!$this->validate($input)) {
            $this->lastError = 'Invalid input';
            return '';
        }
        return "Email sent from {$this->sender}: $input";
    }

    public function getName(): string
    {
        return $this->name;
    }

    protected function getSender(): string
    {
        return $this->sender;
    }
}
